import java.util.function.Consumer;

class Test {
  void f() {
    Consumer<String> x = (String pp<caret>) -> {};
  }
}