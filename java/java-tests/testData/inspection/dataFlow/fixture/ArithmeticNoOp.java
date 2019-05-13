import java.util.*;

public class ArithmeticNoOp {
  void test(long x, int y) {
    if(y == 1) {
      long z = x * y;
      long t = x / y;
      long u = y / z;
      long w = y * z;
      if (<warning descr="Condition 'z == x' is always 'true'">z == x</warning>) {}
      if (<warning descr="Condition 't == x' is always 'true'">t == x</warning>) {}
      if (<warning descr="Condition 'w == x' is always 'true'">w == x</warning>) {}
      if (u == x) {}
    }
    if(y == 0) {
      long a = x >> y;
      long b = x << y;
      long c = x >>> y;
      long d = y >>> x;
      if (<warning descr="Condition 'a == x' is always 'true'">a == x</warning>) {}
      if (<warning descr="Condition 'b == x' is always 'true'">b == x</warning>) {}
      if (<warning descr="Condition 'c == x' is always 'true'">c == x</warning>) {}
      if (d == x) {}
      if (<warning descr="Condition 'd == y' is always 'true'">d == y</warning>) {}
    }
  }
}
