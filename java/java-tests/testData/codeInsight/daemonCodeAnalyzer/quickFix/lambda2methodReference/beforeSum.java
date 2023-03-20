// "Replace lambda with method reference" "true-preview"
import java.util.function.IntBinaryOperator;

class Bar {
  public void test(Object obj) {
    IntBinaryOperator op = (a, b) -> (a) + <caret>b;
  }
}