import java.util.*;

public class FloatingPointCasts {
  void testRandom() {
    double d = Math.random();
    int x = <warning descr="Result of '(int) d' is always '0'">(int) d</warning>;
    long l = <warning descr="Result of '(long) d' is always '0'">(long) d</warning>;
    byte b = <warning descr="Result of '(byte) d' is always '0'">(byte) d</warning>;
  }
  
  void testUpCast(long l, double d1) {
    double d = l;
    if (<warning descr="Condition 'd > 1e20' is always 'false'">d > 1e20</warning>) {}
    if (<warning descr="Condition 'd != d' is always 'false'">d != d</warning>) {}
    if (<warning descr="Condition 'Double.isNaN(d)' is always 'false'">Double.isNaN(d)</warning>) {}
    if (d1 > 1e20) {}
    if (d1 != d1) {}
    if (Double.isNaN(d1)) {}
  }
}