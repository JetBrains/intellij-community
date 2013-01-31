public class NumericLiterals {
  final int FI = 2;
  final int FIBIG = 200000000;

  void f() {
    int i1= -<error descr="Integer number too large">2147483649</error>;
    int i2= <error descr="Integer number too large">2147483648</error>;
    int i3 = -2147483648;
    int i4 = -0x7fffffff;
    int i5 = -017777777777;
    int i6 = <error descr="Hexadecimal numbers must contain at least one hexadecimal digit">0x</error>;
    int i7 = 0xdeadbeef;
    int i8 = 0xffffffff;
    int i9 = <error descr="Integer number too large">0xffffffff9</error>;
    int i10 = 0x80000000;
    int i11 = 0000000000000+0x000000000;
    int i12 = 0x00000000000000000000000;
    int i13 = <error descr="Integer number too large">040000000000</error>;
    int i14 = 020000000000;
    int i15 = <error descr="Integer number too large">0xf044332211</error>;
    int oi1 = 017777777777;
    int oi2 = <error descr="Integer number too large">08</error>;
    int bi1 = <error descr="Binary literals are not supported at this language level">0b0010</error>;
    int bi2 = <error descr="Binary literals are not supported at this language level">0B0010</error>;
    int bi3 = <error descr="Underscores in literals are not supported at this language level">1_2</error>;

    long l1 = -<error descr="Long number too large">9223372036854775809L</error>;
    long l2 = <error descr="Long number too large">9223372036854775808L</error>;
    long l3 = -9223372036854775808L;
    long l4 = -<error descr="Integer number too large">2147483649</error>;
    long l5 = <error descr="Integer number too large">2147483648</error>;
    long l6 = <error descr="Hexadecimal numbers must contain at least one hexadecimal digit">0xL</error>;
    long l7 = 0xdeadbeefffffffffL;
    long l8 = 0xffffffffffffffffL;
    long l9 = <error descr="Long number too large">0xffffffffffffffff9L</error>;
    long l10 = 0x8000000000000000L;
    long ol1 = 01777777777777777777600L;
    long ol2 = 01777777777777777777777L;
    long bl1 = <error descr="Binary literals are not supported at this language level">0b0010l</error>;
    long bl2 = <error descr="Binary literals are not supported at this language level">0B0010L</error>;
    long bl3 = <error descr="Underscores in literals are not supported at this language level">10_24L</error>;

    float f1= <error descr="Floating point number too small">1e-46f</error>;
    float f2 = <error descr="Floating point number too large">1e39f</error>;
    float f3 = 0E1F;
    float f4 = <error descr="Hexadecimal floating point literals are not supported at this language level">0xabc.defP2f</error>;
    float f5 = <error descr="Underscores in literals are not supported at this language level">3.141_592f</error>;
    float f6 = .0f;

    double dd1 = <error descr="Floating point number too small">1e-324</error>;
    double dd2 = <error descr="Floating point number too large">1e309</error>;
    double dd3 = 0E1;
    double dd4 = <error descr="Hexadecimal floating point literals are not supported at this language level">0x1.fffffffffffffP1023</error>;
    double d1 = <error descr="Malformed floating point literal">1.E</error>;
    double d2 = <error descr="Malformed floating point literal">1.e</error>;
    double d3 = <error descr="Malformed floating point literal">1.E+</error>;
    double d4 = <error descr="Malformed floating point literal">1.E-</error>;
    double d5 = <error descr="Malformed floating point literal">.1eD</error>;
    double d6 = <error descr="Malformed floating point literal">.1e+D</error>;
    double d7 = <error descr="Malformed floating point literal">1e+D</error>;
    double d8 = <error descr="Malformed floating point literal">1e-D</error>;
    double d9 = <error descr="Malformed floating point literal">1e-d</error>;
    double d10 = <error descr="Malformed floating point literal">1e-F</error>;
    double d11 = <error descr="Malformed floating point literal">1e-f</error>;
    double d12 = <error descr="Underscores in literals are not supported at this language level">3.141_592_653_589_793d</error>;
    double d13 = <error descr="Malformed floating point literal">.0e</error>;
  }
}
