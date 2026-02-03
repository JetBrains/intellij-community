class Expr {
	private String myString;
	public void meth(String ps, int pns) {
		myString = ps + ps;
		myString = ps + pns;
		myString = pns + ps;
		myString += ps;
	}
}
