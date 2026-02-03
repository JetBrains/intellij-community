class Expr {
	public void meth(long p) {
		long vu1 = p++;
		long vu2 = p--;
		long vu3 = ++p;
		long vu4 = --p;
		long vu5 = -p;
		long vu6 = +p;
		long vu7 = ~p;

		long vb1 = p * p;
		long vb2 = p / p;
		long vb3 = p % p;
		long vb4 = p + p;
		long vb5 = p - p;
		long vb6 = p << p;
		long vb7 = p >> p;
		long vb8 = p >>> p;
		long vb9 = p & p;
		long vba = p ^ p;
		long vbb = p | p;

		long vn1 = 0;
		vn1 *= p;
		long vn2 = 0;
		vn2 /= p;
		long vn3 = 0;
		vn3 %= p;
		long vn4 = 0;
		vn4 += p;
		long vn5 = 0;
		vn5 -= p;
		long vn6 = 0;
		vn6 <<= p;
		long vn7 = 0;
		vn7 >>= p;
		long vn8 = 0;
		vn8 >>>= p;
		long vn9 = 0;
		vn9 &= p;
		long vna = 0;
		vna ^= p;
		long vnb = 0;
		vnb |= p;
	}
}
