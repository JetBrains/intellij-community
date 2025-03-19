/// assignment compatible types
import java.io.*;

public class a  {
  final int FI = 2;
  final int FIBIG = 200000000;


  void f() {
    // not marked: OK, as literal value is in byte-range
    byte b1 = 1;

    // marked: OK, as literal value is out of byte-range and does not compile
    byte b2 = <error descr="Incompatible types. Found: 'int', required: 'byte'">1000;</error>
   
    // marked: OK, as char-value cannot be determined and does not compile
    char c = 0;
    byte b3 = <error descr="Incompatible types. Found: 'char', required: 'byte'">c</error>;

    // marked: OK, as literal char-value is out of byte-range and does not compile
    byte b4 = <error descr="Incompatible types. Found: 'char', required: 'byte'">'\u20AC';</error>

    byte b5 = <error descr="Incompatible types. Found: 'int', required: 'byte'">'\n' - 4 + 1800;</error>
    // literal char-value is in byte-range and compiles fine
    byte b6 = '\u007F';
    byte b7=(short) 0;
    byte b8 = <error descr="Incompatible types. Found: 'long', required: 'byte'">(long)0;</error>

    float f1 = <error descr="Incompatible types. Found: 'double', required: 'float'">77.3;</error>
    float f2 = 77.3F;

    short s1 = 1 + FI;
    short s2 = <error descr="Incompatible types. Found: 'int', required: 'short'">1000000;</error>
    short s3 = 'F' % FIBIG;

    char c1 = 0;
    char c2 = <error descr="Incompatible types. Found: 'int', required: 'char'">-1 + FIBIG;</error>
    char c3=(byte) 0;
    char c4=(short) 0;
    char c5 = <error descr="Incompatible types. Found: 'long', required: 'char'">0L;</error>

    int i1='d';
    int i2 = <error descr="Incompatible types. Found: 'long', required: 'int'">1L;</error>

    Integer destination = 25;
    <error descr="Incompatible types. Found: 'double', required: 'java.lang.Integer'">destination += 2.0</error>;
    Byte b = 1;
    <error descr="Incompatible types. Found: 'int', required: 'java.lang.Byte'">b -= 1</error>;
  }
}