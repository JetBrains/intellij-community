// "Replace method call on lambda with lambda body" "true"

import java.util.function.Consumer;
import java.util.function.Predicate;

public class Main {
  void test() {
    ((Consumer<Predicate<String>>) (x -> x.test("abc"))).ac<caret>cept(String::isEmpty);
  }
}
