// "Replace method call on lambda with lambda body" "true"


public class Main {
  void test() {
    ((Runnable) () -> {}).ru<caret>n();
  }
}
