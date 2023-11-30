// "Rename 'vvv' to '_'" "true-preview"
import java.util.function.Consumer;

class Simple {
  void test() {
    Consumer<String> cons = v<caret>vv -> {};
    cons.accept("");
  }
}