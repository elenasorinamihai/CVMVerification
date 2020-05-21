package terminal;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.javacard.apduio.Apdu;
import com.sun.javacard.apduio.CadClientInterface;
import com.sun.javacard.apduio.CadDevice;
import com.sun.javacard.apduio.CadTransportException;

public class Terminal {

	private CadClientInterface cad;
	private Socket socket;
	Apdu apdu;

	private static final String capFilePath = "C:\\Program Files (x86)\\Oracle\\Java Card Development Kit Simulator 3.1.0\\samples\\classic_applets\\Wallet\\applet\\apdu_scripts\\cap-Wallet.script";
	private static final byte[][] TerminalCVMList = new byte[][] { new byte[] { 50, 95, 6 }, new byte[] { 50, 65, 7 } };
	private static byte[][] CardCvmList;

	// this function connects the terminal to the card's port.
	private void connect() {
		try {
			socket = new Socket("localhost", 9025);
			socket.setTcpNoDelay(true);
			BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
			BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
			// cad means card acceptance device and it represents this terminal(a card
			// reader attached to a desktop computer)
			cad = CadDevice.getCadClientInstance(CadDevice.PROTOCOL_T1, input, output);
		} catch (Exception e) {
			System.out.println("Can not connect to Javacard");
			return;
		}
	}

	// this function "provides" power to the card and it starts the power session
	// (periods of time when there are exchanges of stream of commands)
	private void powerUp() {
		try {
			System.out.println("PowerUp");
			cad.powerUp();
		} catch (Exception e) {
			System.out.println("CAD can not powerup");
			return;
		}
	}

	// this function powers down the card
	private void powerDown() {
		try {
			cad.powerDown();
		} catch (Exception e) {
			System.out.println("CAD can not powerdown");
			return;
		}
	}

	private void parseCAP() {

		try (Stream<String> stream = Files.lines(Paths.get(capFilePath))) {
			// selects all the lines that aren't empty, comments or powerup; and loads the
			// cap file
			stream.filter(s -> !s.isEmpty() && s.charAt(1) != '/' && !s.equals("powerup;")).map(s -> {
				List<String[]> strings = new ArrayList<>();

				String[] splits = s.split(" ");
				strings.add(Arrays.copyOfRange(splits, 0, 4));
				strings.add(Arrays.copyOfRange(splits, 5, splits.length - 1));
				strings.add(Arrays.copyOfRange(splits, splits.length - 1, splits.length));
				return strings;
			}).forEach(strings -> {
				Apdu apdu = new Apdu();

				List<Byte> collect = Arrays.stream(strings.get(0)).map(s -> {
					byte b = 0;
					b += Integer.parseInt(String.valueOf(s.charAt(2)), 16) * 16;
					b += Integer.parseInt(String.valueOf(s.charAt(3)), 16);

					return b;
				}).collect(Collectors.toList());
				byte[] bytes = new byte[4];
				for (int i = 0; i < collect.size(); i++) {
					Byte aByte = collect.get(i);
					bytes[i] = aByte;
				}
				apdu.command = bytes;

				collect = Arrays.stream(strings.get(1)).map(s -> {
					byte b = 0;
					b += Integer.parseInt(String.valueOf(s.charAt(2)), 16) * 16;
					b += Integer.parseInt(String.valueOf(s.charAt(3)), 16);

					return b;
				}).collect(Collectors.toList());
				bytes = new byte[strings.get(1).length];
				for (int i = 0; i < collect.size(); i++) {
					Byte aByte = collect.get(i);
					bytes[i] = aByte;
				}
				byte b = 0;
				b += Integer.parseInt(String.valueOf(strings.get(2)[0].charAt(2)), 16) * 16;
				b += Integer.parseInt(String.valueOf(strings.get(2)[0].charAt(3)), 16);

				apdu.setDataIn(bytes);

				try {
					cad.exchangeApdu(apdu);
				} catch (IOException | CadTransportException e) {
					e.printStackTrace();
				}

				System.out.println(apdu);
			});
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	private void createWallet() {
		// this is the create wallet applet apdu command
		Apdu apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0xB8, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { 0x0a, (byte) 0xa0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0c, 0x06, 0x01, 0x08, 0x00,
				0x00, 0x05, 0x01, 0x02, 0x03, 0x04, 0x05 });
		try {
			cad.exchangeApdu(apdu);
		} catch (IOException | CadTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println(apdu);

	}

	private void selectWallet() {
		// select wallet apdu command
		apdu = new Apdu();
		apdu.command = new byte[] { 0x00, (byte) 0xA4, 0x04, 0x00 };
		apdu.setDataIn(new byte[] { (byte) 0xa0, 0x0, 0x0, 0x0, 0x62, 0x3, 0x1, 0xc, 0x6, 0x1 });
		try {
			cad.exchangeApdu(apdu);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CadTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		System.out.println(apdu);

	}

	private void cardholderListReceiver() throws IOException, CadTransportException {
		// receive the CVM list from the card that should be the same as the terminal
		// CVM list
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x51, 0x00, 0x00 };
		apdu.setDataIn(new byte[] {});
		apdu.setLe(0x14);

		cad.exchangeApdu(apdu);

		System.out.println(apdu);

		// creating the CARD CVM LIST
		CardCvmList = new byte[][] { new byte[] { apdu.getDataOut()[3], apdu.getDataOut()[8], apdu.getDataOut()[9] },
				new byte[] { apdu.getDataOut()[13], apdu.getDataOut()[18], apdu.getDataOut()[19] } };

	}

	private void actions() {
		try {

			System.out.println("\nVerifying the correct card pin (90 00 = Successful Verification): ");
			verifyUserPIN(cad);

			System.out.println("\nCredit 100$ (01 64 = 100 & 90 00 = Successful Transaction): ");
			creditMoney(cad, (byte) 100);

			System.out.println("\nCurrent balance (7F = 100$ & 90 00 = Successful Transaction): ");
			getBalance(cad);

			System.out.println("\nDebit 25$ (19 = 25$ & 90 00 = Successful Transaction): ");
			debitMoney(cad, (byte) 25);

			System.out.println("\nCurrent balance (4b = 75$ & 90 00 = Successful Transaction): ");
			getBalance(cad);

			System.out.println(
					"\nDebit 60$ (second apdu - 3c = 60$ ) + User pin verification(first apdu - 90 00 = Succesful Verification): ");
			debitMoney(cad, (byte) 60);

			System.out.println("\nCurrent balance (0f = 15$ & 90 00 = Successful Transaction): ");
			getBalance(cad);

			System.out.println(
					"\nDebit 60$ (second apdu - 3c = 60$ and 6a 85 = SW_INVALID_TRANSACTION_AMOUNT) + User pin verification(first apdu - 90 00 = Succesful Verification): ");
			debitMoney(cad, (byte) 60);

			System.out.println("\nCurrent balance (0f = 15$ & 90 00 = Successful Transaction): ");
			getBalance(cad);

			verifyInvalidPIN(cad);
			System.out.println("Debit Command (63 01 = SW_PIN_VERIFICATION_REQUIRED): ");
			sendDebitCommand(cad, (byte) 100);

			System.out.println("\nCurrent balance (0f = 15$ & 90 00 = Successful Transaction): ");
			getBalance(cad);

		} catch (IOException | CadTransportException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void verifyUserPIN(CadClientInterface cad) throws IOException, CadTransportException {
		Apdu apdu;// verify PIN
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x20, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 });
		cad.exchangeApdu(apdu);

		System.out.println(apdu);
	}

	private static void creditMoney(CadClientInterface cad, byte sum) throws IOException, CadTransportException {
		Apdu apdu;
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x30, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { sum });
		cad.exchangeApdu(apdu);

		System.out.println(apdu);
	}

	private static void debitMoney(CadClientInterface cad, byte sum) throws IOException, CadTransportException {
		// verifies if the terminal want to use a rule that is defined in the card's cvm
		// rule
		for (byte[] rule : CardCvmList) {
			boolean ok = false;
			for (byte[] terminalRule : TerminalCVMList) {
				ok = true;
				for (int i = 0; i < 3; i++)
					if ((rule[i] == terminalRule[i]) == false) {
						ok = false;
						break;
					}

				if (ok == true)
					break;
			}
			if (!ok)
				continue;

			// verifies if the amount of money we want to spend is under(0x06) or over(0x70)
			// the amount of money in the cvm rule
			if (rule[2] == 0x06) {
				// verifies if it has the command for no cvm required and if the amount of
				// money(sum) we want to spend is less then the amount
				// in the rule.
				if (rule[1] == 0x5f && rule[0] >= sum) {
					sendDebitCommand(cad, sum);
					return;
				}
			} else if (rule[2] == 0x07) {
				// verifies if it has the command for no cvm required and if the amount of
				// money(sum) we want to spend is less then the amount
				// in the rule.
				if (rule[1] == 0x41 && rule[0] < sum) {
					verifyUserPin(cad);
					sendDebitCommand(cad, sum);
					return;
				}
			}
		}
	}

	private static void verifyUserPin(CadClientInterface cad) throws IOException, CadTransportException {
		Apdu apdu;
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x20, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05 });
		cad.exchangeApdu(apdu);

		System.out.println(apdu);
	}

	private static void verifyInvalidPIN(CadClientInterface cad) throws IOException, CadTransportException {
		Apdu apdu;// verify PIN
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x20, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { 0x05, 0x02, 0x09, 0x01, 0x04 });
		cad.exchangeApdu(apdu);

		System.out.println("\nInvalid User Pin Verification (63 00 = SW_VERIFICATION_FAILED): ");
		System.out.println(apdu);
	}

	private static void sendDebitCommand(CadClientInterface cad, byte amount)
			throws IOException, CadTransportException {
		Apdu apdu;
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x40, 0x00, 0x00 };
		apdu.setDataIn(new byte[] { amount });
		cad.exchangeApdu(apdu);
		System.out.println(apdu);

	}

	private static void getBalance(CadClientInterface cad) throws IOException, CadTransportException {
		Apdu apdu;// Get balance
		apdu = new Apdu();
		apdu.command = new byte[] { (byte) 0x80, (byte) 0x50, 0x00, 0x00 };
		cad.exchangeApdu(apdu);

		System.out.println(apdu);
	}

	public static void main(String[] args) throws IOException, CadTransportException {
		Terminal t = new Terminal();
		t.connect();
		t.powerUp();
		t.parseCAP();
		t.createWallet();
		t.selectWallet();
		t.cardholderListReceiver();
		t.actions();
		t.powerDown();
	}
}