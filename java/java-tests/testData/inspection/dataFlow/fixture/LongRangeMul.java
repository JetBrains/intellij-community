import java.util.*;

public class LongRangeMul {
  void test(int x, int y) {
    if (<warning descr="Condition 'x == 5 && y == 10 && x * y != 50' is always 'false'">x == 5 && y == 10 && <warning descr="Condition 'x * y != 50' is always 'false'">x * y != 50</warning></warning>) {
    }
    if (x < 0) {
      x = -1 * x;
    }
    if (y < 0) {
      y = -1 * y;
    }
    if (x < 0 && y < 0) {
      x *= y;
      if (<warning descr="Condition 'x != 0' is always 'false'">x != 0</warning>) {}
    }
  }
}
