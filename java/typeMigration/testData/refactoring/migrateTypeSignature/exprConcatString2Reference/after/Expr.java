class Expr {
	private int myString;
	public void meth(Object ps, int pns) {
		myString = ps + ps;
		myString = ps + pns;
		myString = pns + ps;
		myString += ps;
	}
}
