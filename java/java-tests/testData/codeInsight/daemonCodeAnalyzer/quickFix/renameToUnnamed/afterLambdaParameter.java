// "Rename 'vvv' to '_'" "true-preview"
import java.util.function.Consumer;

class Simple {
  void test() {
    Consumer<String> cons = _ -> {};
    cons.accept("");
  }
}