/// assignment compatible types
import java.io.*;
import java.net.*;
public class a  {
  final int FI = 2;
  final int FIBIG = 200000000;


  void f() {
    // not marked: OK, as literal value is in byte-range
    byte b1 = 1;

    // marked: OK, as literal value is out of byte-range and does not compile
    <error descr="Incompatible types. Found: 'int', required: 'byte'">byte b2 = 1000;</error>
   
    // marked: OK, as char-value cannot be determined and does not compile
    char c = 0;
    <error descr="Incompatible types. Found: 'char', required: 'byte'">byte b3 = c;</error>

    // marked: OK, as literal char-value is out of byte-range and does not compile
    <error descr="Incompatible types. Found: 'char', required: 'byte'">byte b4 = '\u20AC';</error>

    <error descr="Incompatible types. Found: 'int', required: 'byte'">byte b5 = '\n' - 4 + 1800;</error>
    // literal char-value is in byte-range and compiles fine
    byte b6 = '\u007F';
    byte b7=(short) 0;
    <error descr="Incompatible types. Found: 'long', required: 'byte'">byte b8 = (long)0;</error>

    <error descr="Incompatible types. Found: 'double', required: 'float'">float f1 = 77.3;</error>
    float f2 = 77.3F;

    short s1 = 1 + FI;
    <error descr="Incompatible types. Found: 'int', required: 'short'">short s2 = 1000000;</error>
    short s3 = 'F' % FIBIG;

    char c1 = 0;
    <error descr="Incompatible types. Found: 'int', required: 'char'">char c2 = -1 + FIBIG;</error>
    char c3=(byte) 0;
    char c4=(short) 0;
    <error descr="Incompatible types. Found: 'long', required: 'char'">char c5 = 0L;</error>

    int i1='d';
    <error descr="Incompatible types. Found: 'long', required: 'int'">int i2 = 1L;</error>

  }
}