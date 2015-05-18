package com.example.pdsdt2;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

public class MainActivity extends Activity {

	protected EditText portServer;
	protected EditText portClient;
	protected EditText adresa;
	protected EditText city;
	protected Spinner infoType;
	protected TextView weatherForecastTextView;
	private HashMap<String, Informations> informations;
	ServerThread serverThread;

	private boolean serverStateRunning;

	public Informations getInformation(String oras) {
		Informations info = null;
		try {
			HttpClient httpClient = new DefaultHttpClient();
			HttpGet httpGet = new HttpGet("http://www.wunderground.com/cgi-bin/findweather/getForecast?query=" + oras);
			ResponseHandler<String> responseHandler = new BasicResponseHandler();
			String content = httpClient.execute(httpGet, responseHandler);

			Element htmlTag = Jsoup.parse(content).child(0);

			info = new Informations();
			info.umiditate = htmlTag.getElementsByAttributeValue(
					"data-variable", "humidity").text();
			info.presiune = htmlTag.getElementsByAttributeValue(
					"data-variable", "pressure").text();
			info.temperatura = htmlTag.getElementsByAttributeValue(
					"data-variable", "temperature").text();
			info.stareGenerala = htmlTag.getElementsByAttributeValue(
					"data-variable", "feelslike").text();
			info.vitezaVant = htmlTag.getElementsByAttributeValue(
					"data-variable", "wind_gust_speed").text();

		} catch (Exception exception) {
			exception.printStackTrace();
		}

		return info;
	}

	class CommunicationThread extends Thread {
		private Socket socket;

		public CommunicationThread(Socket socket) {
			this.socket = socket;
		}

		@Override
		public void run() {
			super.run();
			try {
				BufferedReader bufferedReader = Utilities.getReader(socket);
				String oras = bufferedReader.readLine();
				String info = bufferedReader.readLine();

				Informations newInfo;
				if (informations.containsKey(oras)) {
					newInfo = informations.get(oras);
				} else {
					newInfo = getInformation(oras);
					informations.put(oras, newInfo);
				}

				PrintWriter printWriter = Utilities.getWriter(socket);
				if (info.equals("all")) {
					printWriter.println(newInfo.presiune + " "
							+ newInfo.temperatura + " " + newInfo.stareGenerala
							+ " " + newInfo.umiditate + " "
							+ newInfo.vitezaVant);
				} else if (info.equals("temperatura")) {
					printWriter.println(newInfo.temperatura);
				} else if (info.equals("viteza vantului")) {
					printWriter.println(newInfo.vitezaVant);
				}
				printWriter.flush();
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	class ServerThread extends Thread {
		private ServerSocket serverSocket;

		public ServerThread() {
			informations = new HashMap<String, Informations>();
		}

		public void closeSocket() {
			new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						if (serverSocket != null) {
							serverSocket.close();
						}
					} catch (IOException ioException) {
						ioException.printStackTrace();
					}
				}
			}).start();
		}

		@Override
		public void run() {
			super.run();
			try {
				serverSocket = new ServerSocket(Integer.parseInt(portServer.getText().toString()));
				
				while (serverStateRunning) {
					Socket socket = serverSocket.accept();
					new CommunicationThread(socket).start();
				}

			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	class ClientThread extends Thread {
		
		@Override
		public void run() {
			super.run();
			try {
				Socket socket = new Socket(adresa.getText().toString(), Integer.parseInt(portClient.getText().toString()));
				String oras = city.getText().toString();
				String infoTypeRequired = infoType.getSelectedItem().toString();
				
				PrintWriter printWriter = Utilities.getWriter(socket);
				printWriter.println(oras);
				printWriter.println(infoTypeRequired);
				printWriter.flush();
				
				BufferedReader bufferedReader = Utilities.getReader(socket);
				final String result  = bufferedReader.readLine();
				
				weatherForecastTextView.post(new Runnable() {
					@Override
					public void run() {
						weatherForecastTextView.setText(result);
					}
				});
				socket.close();
			} catch (NumberFormatException e) {
				e.printStackTrace();
			} catch (UnknownHostException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		portServer = (EditText) findViewById(R.id.server_port_edit_text);

		adresa = (EditText) findViewById(R.id.client_address_edit_text);
		portClient = (EditText) findViewById(R.id.client_port_edit_text);
		city = (EditText) findViewById(R.id.city_edit_text);
		infoType = (Spinner) findViewById(R.id.information_type_spinner);
		
		weatherForecastTextView = (TextView) findViewById(R.id.weather_forecast_text_view);

		final Button buttonConectServer = (Button) findViewById(R.id.connect_button);
		Button buttonGetWeather = (Button) findViewById(R.id.get_weather_forecast_button);

		serverStateRunning = false;
		
		buttonConectServer.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				if (!serverStateRunning) {
					serverStateRunning = true;
					buttonConectServer.setText("Disconnect");
					serverThread = new ServerThread();
					serverThread.start();
				} else {
					buttonConectServer.setText("Connect");
					serverStateRunning = false;
					serverThread.closeSocket();
				}
			}
		});
		
		buttonGetWeather.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				new ClientThread().start();
			}
		});
		
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
}
