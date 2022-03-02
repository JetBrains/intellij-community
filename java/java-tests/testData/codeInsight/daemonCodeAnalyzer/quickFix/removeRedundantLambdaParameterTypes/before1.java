// "Remove 'var'" "true"
import java.util.function.Function;

class Main {
  void foo() {
    Function<String, Integer> deepThought = (var<caret> question) -> 42;
  }
}
