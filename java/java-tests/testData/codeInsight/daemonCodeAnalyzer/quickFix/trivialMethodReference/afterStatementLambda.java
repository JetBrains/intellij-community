// "Replace with qualifier" "true"
import java.util.function.Consumer;

class Test {
  void foo(Consumer<String> consumer) {
    Consumer<String> another = consumer;
  }
}