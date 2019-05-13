import java.util.*;

public class LongRangeDivShift {
  void test(int[] arr, int x) {
    if(<warning descr="Condition 'arr.length / 2 < 0' is always 'false'">arr.length / 2 < 0</warning>) {
      System.out.println("Impossible");
    }
  }

  void signTest(int x, int y) {
    if(<warning descr="Condition 'x > 0 && y < 0 && x/y > 0' is always 'false'">x > 0 && y < 0 && <warning descr="Condition 'x/y > 0' is always 'false'">x/y > 0</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void shift(long x) {
    long a = x >> 32;
    if(<warning descr="Condition 'a < Integer.MIN_VALUE || a > Integer.MAX_VALUE' is always 'false'"><warning descr="Condition 'a < Integer.MIN_VALUE' is always 'false'">a < Integer.MIN_VALUE</warning> || <warning descr="Condition 'a > Integer.MAX_VALUE' is always 'false' when reached">a > Integer.MAX_VALUE</warning></warning>) {
      System.out.println("Impossible");
    }
  }

  void shiftUnsigned(int x) {
    x = x >>> 16;
    if(<warning descr="Condition 'x >= 0 && x <= 0xFFFF' is always 'true'"><warning descr="Condition 'x >= 0' is always 'true'">x >= 0</warning> && <warning descr="Condition 'x <= 0xFFFF' is always 'true' when reached">x <= 0xFFFF</warning></warning>) {
      char c = (char)x;
    }
  }

  static final int RESIZE_STAMP_SHIFT = 16;
  static final int MAX_RESIZERS = 65535;

  void testCHM(int sc, int rs) {
    if (sc < 0) {
      if ((sc >>> RESIZE_STAMP_SHIFT) != rs || <warning descr="Condition 'sc == rs + 1' is always 'false'">sc == rs + 1</warning> ||
          <warning descr="Condition 'sc == rs + MAX_RESIZERS' is always 'false'">sc == rs + MAX_RESIZERS</warning>) {}
    }
  }
}
