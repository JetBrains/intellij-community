class Holder implements Cloneable, java.io.Serializable {}

class Type {
	private Object myObject;
	private Cloneable myCloneable;
	private java.io.Serializable mySerializable;
	private Holder myHolder;

	public void meth(Holder p) {
		myObject = p;
		myCloneable = p;
		mySerializable = p;
		myHolder = p;
	}
}
