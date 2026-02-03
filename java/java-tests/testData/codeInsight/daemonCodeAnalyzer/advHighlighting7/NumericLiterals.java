public class NumericLiterals {
  void f() {
    int i1 = 1_2;
    int i2 = 012__34;
    int i3 = 0x1_2_3_4;
    int i4 = 0B0;
    int i5 = 0b0001_0010_0100_1000;
    int i6 = <error descr="Binary numbers must contain at least one binary digit">0b</error>;
    int i7 = <error descr="Integer number too large">0b1_1111_1111_1111_1111_1111_1111_1111_1111</error>;
    int i8 = <error descr="Illegal underscore">0_</error>;
    int i9 = <error descr="Integer number too large">0_8</error>;
    int i10 = <error descr="Illegal underscore">0x_f</error>;
    int i11 = <error descr="Illegal underscore">0b_1</error>;
    int i12 = <error descr="Integer number too large">0B2</error>;

    long l1 = 1_2L;
    long l2 = 012__34l;
    long l3 = 0x1_2_3_4L;
    long l4 = 0B0L;
    long l5 = 0b0001_0010_0100_1000l;
    long l6 = <error descr="Binary numbers must contain at least one binary digit">0Bl</error>;
    long l7 = <error descr="Long number too large">0B1_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111_1111L</error>;
    long l8 = <error descr="Long number too large">9_223_372_036_854_775_808L</error>;
    long l9 = -9_223_372_036_854_775_808L;

    float f1 = 1_0f;
    float f2 = 1e1_2f;
    float f3 = 2_2.f;
    float f4 = .3_3f;
    float f5 = 3.14_16f;
    float f6 = 6.022___137e+2_3f;
    float f7 = 0xa_ap1_0f;
    float f8 = 0xa_b.p22F;
    float f9 = 0x.ab__cP0f;
    float f10 = 0xa_bc.d_efP0F;
    float f11= <error descr="Floating point number too small">1e-4__6f</error>;
    float f12 = <error descr="Floating point number too large">1e3_9f</error>;
    float f13 = 0x0.0p1f;

    double d1 = 0_0d;
    double d2 = 1e1_1;
    double d3 = 2_2.;
    double d4 = .3_3;
    double d5 = 3.141_592;
    double d6 = 1e-9_9d;
    double d7 = 1e1__3_7;
    double d8 = 0xa_ap1;
    double d9 = 0xa_b.P1_2;
    double d10 = 0x.a_bcP1___23d;
    double d11 = <error descr="Floating point number too small">1e-3_2_4</error>;
    double d12 = <error descr="Floating point number too large">0xa_bc.de_fP1_234D</error>;
    double d13 = <error descr="Illegal underscore">0x1.0_p-1</error>;
    double d14 = <error descr="Illegal underscore">1.0e_1022</error>;
    double d15 = 0x.0P2d;
  }
}
