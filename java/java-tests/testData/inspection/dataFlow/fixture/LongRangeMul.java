import java.util.*;

public class LongRangeMul {
  void testShift(int n, int sc) {
    if (n > 0) {
      if ((sc - 2) != calc(n) << 16)
      {}
    }
  }

  native int calc(int n);

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

  void additionAsMultiplication(int x, int y) {
    if (x == y) {
      int z = x + y;
      if (<warning descr="Condition 'z % 2 == 1' is always 'false'"><warning descr="Result of 'z % 2' is always '0'">z % 2</warning> == 1</warning>) {
      }
    }
    if (x + y == 1 && <warning descr="Condition 'x != y' is always 'true' when reached">x != y</warning>) {}
  }
}
