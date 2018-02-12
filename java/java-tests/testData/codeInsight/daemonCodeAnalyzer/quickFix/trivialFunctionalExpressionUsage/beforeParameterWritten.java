// "Replace method call on lambda with lambda body" "false"

import java.util.function.IntUnaryOperator;

public class Main {
  void test() {
    ((IntUnaryOperator)x -> x+=5).app<caret>lyAsInt(10);
  }
}
