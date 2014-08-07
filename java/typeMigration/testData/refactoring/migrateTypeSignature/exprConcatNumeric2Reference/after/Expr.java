class Expr {
	private String myString;
	public void meth(Object pns, String ps) {
		myString = ps + ps;
		myString = ps + pns;
		myString = pns + ps;
		myString += ps;
	}
}
