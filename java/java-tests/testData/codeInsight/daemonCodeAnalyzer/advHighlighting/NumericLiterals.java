/// assignment compatible types
import java.io.*;
import java.net.*;
public class a  {
  final int FI = 2;
  final int FIBIG = 200000000;


  void f() {
   int i1=-<error descr="Integer number too large">2147483649</error>;
   int i2=<error descr="Integer number too large">2147483648</error>;
   int i3 = -2147483648;
   int i4 = -0x7fffffff;
   int i5 = -017777777777;
   int i6 = <error descr="Hexadecimal numbers must contain at least one hexadecimal digit">0x</error>;
   int i7 = 0xdeadbeef;
   int i8 = 0xffffffff;
   int i9=<error descr="Integer number too large">0xffffffff9</error>;
   int i10= 0x80000000;
   int i11=0000000000000+0x000000000;
   int i12=0x00000000000000000000000;
   int i13= <error descr="Integer number too large">040000000000</error>;
   int i14=          020000000000;
   int i15 = <error descr="Integer number too large">0xf044332211</error>;
   int octale =      017777777777;    // negative



   long l1=-<error descr="Long number too large">9223372036854775809L</error>;
   long l2=<error descr="Long number too large">9223372036854775808L</error>;
   long l3=-9223372036854775808L;
   long l4=-<error descr="Integer number too large">2147483649</error>;
   long l5=<error descr="Integer number too large">2147483648</error>;
   long l6 = <error descr="Hexadecimal numbers must contain at least one hexadecimal digit">0xL</error>;
   long l7= 0xdeadbeefffffffffL;
   long l8= 0xffffffffffffffffL;
   long l9=<error descr="Long number too large">0xffffffffffffffff9L</error>;
   long l10=0x8000000000000000L;
   long octalValue = 01777777777777777777600L;
   long octalValua = 01777777777777777777777L;

   float f1= <error descr="Floating point number too small">1e-46f</error>;
   float f2=<error descr="Floating point number too large">1e39f</error>;
   float f3=0E1F;

   double dd1=<error descr="Floating point number too small">1e-324</error>;
   double dd2=<error descr="Floating point number too large">1e309</error>;
   double dd3=0E1;

    double d1=<error descr="Malformed floating point literal">1.E</error>;
    double d2=<error descr="Malformed floating point literal">1.e</error>;
    double d3=<error descr="Malformed floating point literal">1.E+</error>;
    double d4=<error descr="Malformed floating point literal">1.E-</error>;
    double d5=<error descr="Malformed floating point literal">.1eD</error>;
    double d6=<error descr="Malformed floating point literal">.1e+D</error>;
    double d7=<error descr="Malformed floating point literal">1e+D</error>;
    double d8=<error descr="Malformed floating point literal">1e-D</error>;
    double d9=<error descr="Malformed floating point literal">1e-d</error>;
    double d10=<error descr="Malformed floating point literal">1e-F</error>;
    double d11=<error descr="Malformed floating point literal">1e-f</error>;


  }
}
