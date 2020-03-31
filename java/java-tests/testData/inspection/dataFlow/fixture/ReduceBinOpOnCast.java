import java.util.*;

public class ReduceBinOpOnCast {
  // IDEA-231598
  private void doByte(byte b) {
    System.out.println(b);
    if (b < 0) {
      <warning descr="Variable update does nothing">b</warning> +=256;
    }
    System.out.println(b);
  }

  void test(int a, short b) {
    if (a == 0xFFFFF) {
      int res = (short)(b + a + 1);
      if (<warning descr="Condition 'res == b' is always 'true'">res == b</warning>) {}
    }
  }
  
  void testTwoChecks(int a, short b) {
    if (a == 0x10000 || a == 0x20000 || a == 0) {
      <warning descr="Variable update does nothing">b</warning> += a;
    }
  }
  
  void testDoubleCast(int x) {
    double d = x + 1;
  }
}
