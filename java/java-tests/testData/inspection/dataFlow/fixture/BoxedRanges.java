import java.util.*;

class Test {
  void testBoxed(Integer x) {
    if(<warning descr="Condition 'x > 0 || x < 10' is always 'true'">x > 0 || <warning descr="Condition 'x < 10' is always 'true' when reached">x < 10</warning></warning>) {}
  }

  void testMixed(Long l) {
    long unbox = l;
    if(<warning descr="Condition 'l.longValue() > 0 || unbox < 10' is always 'true'">l.longValue() > 0 || <warning descr="Condition 'unbox < 10' is always 'true' when reached">unbox < 10</warning></warning>) {}
  }

  void test(Integer x, String[] data) {
    if(x < 0 && data[<warning descr="Array index is out of bounds">x</warning>].isEmpty()) {}
  }
  
  void testRangeOutOfThinAir(int x) {
    Integer mod = x % 10;
    if (<warning descr="Condition 'mod > 10' is always 'false'">mod > 10</warning>) {
      System.out.println("Impossible");
    }
  }
}
