// "Replace nested function call with andThen call" "true"

import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class Main {
  private void testFn() {
    Function<String, Integer> lookup = Integer::parseInt;
    UnaryOperator<String> selector = String::trim;
    BinaryOperator<Integer> min = Math::min;
    String foo = "xyz";

    Integer integer = selector.andThen(lookup).apply("  " + foo);
  }
}
