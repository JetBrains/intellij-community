class Expr {
	private boolean[][] myField;
	public void meth(boolean p) {
		myField = new boolean[][]{{p}, {!p}, {true}, {0}};
	}
}
