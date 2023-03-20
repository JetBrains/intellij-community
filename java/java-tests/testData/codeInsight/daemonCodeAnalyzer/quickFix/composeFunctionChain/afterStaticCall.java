// "Replace nested function call with andThen call" "true-preview"

import java.util.function.UnaryOperator;

public class Main {
  private void testFn(boolean b) {
    String foo = "xyz";

    Integer f = (b ? (UnaryOperator<String>) String::trim : (UnaryOperator<String>) s -> s.substring(1)).andThen(Integer::parseInt).apply(foo);
  }
}
