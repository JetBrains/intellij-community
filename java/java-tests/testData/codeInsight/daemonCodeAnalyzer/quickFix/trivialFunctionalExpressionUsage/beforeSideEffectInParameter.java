// "Replace method call on lambda with lambda body" "true"

import java.util.function.IntBinaryOperator;

public class Main {
  void test() {
    int z = 0;
    int res = ((IntBinaryOperator)(x, y) -> x).applyAs<caret>Int(z+=2, z+=3);
  }
}
