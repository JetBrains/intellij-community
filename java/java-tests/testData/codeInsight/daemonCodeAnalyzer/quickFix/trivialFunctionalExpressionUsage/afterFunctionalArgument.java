// "Replace method call on lambda with lambda body" "true"

import java.util.function.Consumer;
import java.util.function.Predicate;

public class Main {
  void test() {
    ((Predicate<String>) String::isEmpty).test("abc");
  }
}
