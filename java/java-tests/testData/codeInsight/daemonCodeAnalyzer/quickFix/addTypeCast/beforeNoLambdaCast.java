// "Cast 1st argument to 'Consumer<String>'" "false"
import java.util.function.Consumer;

class Test {
  void foo(Consumer<String> cons, String a) {
  }

  void foo(Consumer<String> cons, Character a) {
  }

  void test() {
    foo(s -><caret>
          System.out.println(s.length()), 1);
  }
}