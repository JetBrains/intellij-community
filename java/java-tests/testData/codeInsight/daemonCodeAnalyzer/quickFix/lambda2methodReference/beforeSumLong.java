// "Replace lambda with method reference" "true"
import java.util.function.LongBinaryOperator;

class Bar {
  public void test(Object obj) {
    LongBinaryOperator op = (a, b) -> b + <caret>a;
  }
}