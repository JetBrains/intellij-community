class Expr {
	public void meth(int p) {
		int vu1 = p++;
		int vu2 = p--;
		int vu3 = ++p;
		int vu4 = --p;
		int vu5 = -p;
		int vu6 = +p;
		int vu7 = ~p;

		int vb1 = p * p;
		int vb2 = p / p;
		int vb3 = p % p;
		int vb4 = p + p;
		int vb5 = p - p;
		int vb6 = p << p;
		int vb7 = p >> p;
		int vb8 = p >>> p;
		int vb9 = p & p;
		int vba = p ^ p;
		int vbb = p | p;

		int vn1 = 0;
		vn1 *= p;
		int vn2 = 0;
		vn2 /= p;
		int vn3 = 0;
		vn3 %= p;
		int vn4 = 0;
		vn4 += p;
		int vn5 = 0;
		vn5 -= p;
		int vn6 = 0;
		vn6 <<= p;
		int vn7 = 0;
		vn7 >>= p;
		int vn8 = 0;
		vn8 >>>= p;
		int vn9 = 0;
		vn9 &= p;
		int vna = 0;
		vna ^= p;
		int vnb = 0;
		vnb |= p;
	}
}
