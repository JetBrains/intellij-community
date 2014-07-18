class Expr {
	private String myString;
	public void meth(String pns, String ps) {
		myString = ps + ps;
		myString = ps + pns;
		myString = pns + ps;
		myString += ps;
	}
}
