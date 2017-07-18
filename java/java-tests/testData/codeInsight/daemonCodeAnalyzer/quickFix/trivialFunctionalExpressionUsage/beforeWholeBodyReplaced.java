// "Replace method call on lambda with lambda body" "true"

import java.util.function.IntBinaryOperator;

public class Main {
  void test() {
    int res = ((IntBinaryOperator)(x, y) -> x).appl<caret>yAsInt(5, 6);
  }
}
