// "Replace with qualifier" "true-preview"
import java.util.function.Function;

class Test {
  void foo(Function<String, String> function) {
    Function<String, String> another = function;
  }
}