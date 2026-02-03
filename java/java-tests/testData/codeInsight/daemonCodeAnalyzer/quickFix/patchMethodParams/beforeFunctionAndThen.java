// "Cast lambda return to 'long'" "true-preview"
import java.util.function.*;

class Demo {
  void test(Function<String, String> input) {
    Function<String, Long> result = input.andThen(s -> <caret>s.length());
  }
}
