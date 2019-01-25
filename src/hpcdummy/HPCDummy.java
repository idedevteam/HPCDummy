/**
 * 
 */
package hpcdummy;

import javacard.framework.Applet;
import javacard.framework.ISOException;
import javacard.framework.ISO7816;
import javacard.framework.APDU;
import javacard.framework.JCSystem;
import javacard.framework.OwnerPIN;
import javacard.framework.Util;
import javacardx.apdu.ExtendedLength;

/**
 * @author e-health
 *
 */
public class HPCDummy extends Applet implements ExtendedLength {
	static final byte CLA = (byte) 0x80;
	final static byte INS_SET_PIN = (byte) 0xC2;
	final static byte INS_OWNER_AUTH = (byte) 0xC3;
	final static byte INS_CHANGE_PIN = (byte) 0xC4;
	final static byte INS_READ_HPDATA = (byte) 0xD1;
	final static byte INS_READ_CERT = (byte) 0xD2;
	final static byte INS_CARD_CHECKING = (byte) 0xE1;
	final static byte INS_SET_CERT = (byte) 0xF1;
	final static byte INS_SET_HPDATA = (byte) 0xF2;
	
	final static byte PIN_TRY_LIMIT = 0x03;
	final static byte PIN_SIZE = 0x05;
	
	static boolean[] isVerified;
	
	private byte[] pin = new byte[5]; 
	private byte[] cert = new byte[17]; // holder role (1), nik (16)
	private byte[] hpData = new byte[100]; // NIK(16) nama (max 50) SIP (?)
	private byte[] pinBuf = new byte[6];
//	static final short MAX_LENGTH = (short)(0x01F4);
	static final short HPDATA_LENGTH = (short)(0x64);
	static final short CERT_LENGTH = (short)(0x11);
	
	static byte lifeState; //persistent memory
	/* nilai untuk lifeState */
	final static byte LIFE_STATE_VIRGIN 			= 0x00;
	final static byte LIFE_HALF_PERSONALIZED 		= 0x01;
	final static byte LIFE_STATE_PERSONALIZED 		= 0x11;
	
	// isi applet: pin, certificate (holder Role, NIK)
	
//	OwnerPIN pin;
	
	private HPCDummy(byte[] bArray, short bOffset, byte bLength) {
		register(bArray, (short) (bOffset+1), bArray[bOffset]);
//		pin = new OwnerPIN(PIN_TRY_LIMIT, PIN_SIZE);
		short appletDataOff = (short) (1 + bArray[0]);
		appletDataOff += (short) (1 + bArray[appletDataOff]);
//		pin.update(bArray, (short)(appletDataOff+1), PIN_SIZE);
		
		// transient memory, supaya pas dideselect pinnya kereset lagi
		isVerified = JCSystem.makeTransientBooleanArray((short) 1, JCSystem.CLEAR_ON_DESELECT);
		lifeState = LIFE_STATE_VIRGIN;
	}
	
	public static void install(byte[] bArray, short bOffset, byte bLength) {
		// GP-compliant JavaCard applet registration
		new HPCDummy(bArray, bOffset, bLength);
	}
	
//	public boolean select() {
//		if (pin.getTriesRemaining() == 0) {
//			return false;
//		}
//		return true;
//	}

	public void process(APDU apdu) {
		// Good practice: Return 9000 on SELECT
		if (selectingApplet()) {
			return;
		}
		
		short recvLen = apdu.setIncomingAndReceive();
		short lc = apdu.getIncomingLength();

		byte[] buf = apdu.getBuffer();
		switch (buf[ISO7816.OFFSET_INS]) {
		case INS_SET_PIN:
			setPin(apdu, buf, recvLen);
			break;
		case INS_SET_CERT:
			setCert(apdu, buf, recvLen);
			break;
		case INS_SET_HPDATA:
			setHPData(apdu, buf, recvLen);
			break;
		case INS_CARD_CHECKING:
			cardChecking(apdu);
			break;
		case INS_OWNER_AUTH:
			ownerAuth(apdu, buf, recvLen);
			break;
		case INS_READ_HPDATA:
			readHPData(apdu);
			break;
		case INS_READ_CERT:
			readCert(apdu);
			break;
		default:
			// good practice: If you don't know the INStruction, say so:
			ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
		}
	}
	
	void setCert(APDU apdu, byte[] buf, short recvLen) {
		if (lifeState != LIFE_STATE_VIRGIN) {
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		}
		short pointer = 0;
		short offData = apdu.getOffsetCdata();
		while (recvLen > (short)0) {
			Util.arrayCopyNonAtomic(buf, offData, cert, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(offData);
		}
		
		lifeState = LIFE_HALF_PERSONALIZED;
	}
	
	void setHPData(APDU apdu, byte[] buf, short recvLen) {
		if (lifeState != LIFE_HALF_PERSONALIZED) {
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		}
		short pointer = 0;
		short offData = apdu.getOffsetCdata();
		while (recvLen > (short)0) {
			Util.arrayCopyNonAtomic(buf, offData, hpData, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(offData);
		}
		
		lifeState = LIFE_STATE_PERSONALIZED;
	}
	
	void cardChecking(APDU apdu){
		byte buffer[] = apdu.getBuffer();
		buffer[0] = lifeState; 
		apdu.setOutgoingAndSend((short)0, (short)1);
	}
	
//	void setPin(APDU apdu, byte[] buf, short recvLen) {
//		if (!isVerified[0]) {
//			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
//		}
//		short pointer = 0;
//		short offData = apdu.getOffsetCdata();
//		while (recvLen > (short)0) {
//			Util.arrayCopyNonAtomic(buf, offData, pinBuf, pointer, recvLen);
//			pointer += recvLen;
//			recvLen = apdu.receiveBytes(offData);
//		}
//		pin.update(pinBuf, (short)0, (byte)6);
//	}
	
	void setPin(APDU apdu, byte[] buf, short recvLen) {
		short pointer = 0;
		short offData = apdu.getOffsetCdata();
		while (recvLen > (short)0) {
			Util.arrayCopyNonAtomic(buf, offData, pin, pointer, recvLen);
			pointer += recvLen;
			recvLen = apdu.receiveBytes(offData);
		}
	}

	void readHPData(APDU apdu) {
		if (!isVerified[0]) {
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		}
		apdu.setOutgoing();
		apdu.setOutgoingLength((short) (HPDATA_LENGTH));
		apdu.sendBytesLong(hpData, (short)0, HPDATA_LENGTH);
	}
	
	void readCert(APDU apdu) {
		if (!isVerified[0]) {
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		}
		apdu.setOutgoing();
		apdu.setOutgoingLength((short) (CERT_LENGTH));
		apdu.sendBytesLong(cert, (short)0, CERT_LENGTH);
	}
	
	void ownerAuth(APDU apdu, byte[] buffer, short recvLen) {
//		byte byteRead = (byte) recvLen;
//		if (!pin.check(buffer, ISO7816.OFFSET_EXT_CDATA, byteRead)) {
//			ISOException.throwIt(ISO7816.SW_DATA_INVALID);
//		}
//		isVerified[0] = true;
		byte[] tempPin = new byte[5];
		short offData = apdu.getOffsetCdata();
		Util.arrayCopyNonAtomic(buffer, offData, tempPin, (short)0, (short)5);
		if (Util.arrayCompare(pin, (short)0, tempPin, (short)0, (short)5) == 0) {
			isVerified[0] = true;
		} else {
			ISOException.throwIt(ISO7816.SW_CONDITIONS_NOT_SATISFIED);
		}
	}
}