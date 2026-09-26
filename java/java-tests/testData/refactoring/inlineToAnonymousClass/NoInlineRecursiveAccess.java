public class SelfParams {
	private int myVar = 0;

	private int myProp = 0;
	public int getProp() {
		return myProp;
	}
	public void setProp(int prop) {
		myProp = prop;
	}

	public void copy(SelfParams sp) {
		this.myVar = sp.myVar;
		this.myProp = sp.getProp();
	}

  public SelfParams clone() {
    return new SelfParams();
  }
}

class Usage {
  SelfParams s = new Self<caret>Params();
}