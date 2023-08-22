import java.util.*;

public class UnaryPlusMinus {
  void test() {
    int x = 0;
    if (<warning descr="Condition 'x == 0' is always 'true'">x == 0</warning>) { }

    x += 1;
    if (<warning descr="Condition 'x == 1' is always 'true'">x == 1</warning>) { }

    x++;
    if (<warning descr="Condition 'x == 3' is always 'false'">x == 3</warning>) { }

    ++x;
    if (<warning descr="Condition 'x == 3' is always 'true'">x == 3</warning>) {}

    if (<warning descr="Condition '--x == 2' is always 'true'">--x == 2</warning>) {}
    x--;
    if (<warning descr="Condition 'x == 1' is always 'true'">x == 1</warning>) {}
  }

  void testChar() {
    char c = 0;
    if (<warning descr="Condition '--c == '\uFFFF'' is always 'true'">--c == '\uFFFF'</warning>) {}
  }

  void testLong() {
    long l = Integer.MAX_VALUE;
    l++;
    if (<warning descr="Condition 'l == Integer.MAX_VALUE+1L' is always 'true'">l == Integer.MAX_VALUE+1L</warning>) {}
  }

  void testDouble() {
    double x = 0;
    x++;
    if (<warning descr="Condition 'x == 1' is always 'true'">x == 1</warning>) {}
    x = 1e15;
    x++;
    if (<warning descr="Condition 'x == 1e15' is always 'false'">x == 1e15</warning>) {}
  }

  void testArray() {
    int[] x = new int[3];
    int index = 0;
    x[index++] = 1;
    x[index++] = 2;
    x[index++] = 3;
    x[<warning descr="Array index is out of bounds">index++</warning>] = 4;
  }

  int testInForCondition(int[] _data, int _pos) {
    int max = _data[_pos - 1];
    for (int i = _pos - 1; i-- > 0;) {
      max = Math.max(max, _data[_pos]);
    }
    return max;
  }

  void testNotComplexInLoop() {
    int x = 0;
    while(true) {
      int y = x + 1;
      if (y > 10000) break;
      x = y;
    }
    System.out.println(x);
  }
}
