class Expr {
	private boolean[] myArrayOne;
	private boolean[][] myArrayTwo;
	public void meth(boolean p) {
		myArrayOne = new boolean[]{p};
		myArrayTwo = new boolean[][]{{p}, {!p}};
	}
}
