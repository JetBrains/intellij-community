class Expr {
	public void meth(int pb) {
		int vba = pb & pb;
		int vbb = pb ^ pb;
		int vbc = pb | pb;
		boolean vbd = pb && pb;
		boolean vbe = pb || pb;

		boolean vn1 = false;
		vn1 &= pb;
		boolean vn2 = false;
		vn2 ^= pb;
		boolean vn3 = false;
		vn3 |= pb;
	}
}
