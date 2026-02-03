// "Replace lambda with method reference" "true-preview"
import java.util.function.LongBinaryOperator;

class Bar {
  public void test(Object obj) {
    LongBinaryOperator op = Long::sum;
  }
}