// "Replace method call on lambda with lambda body" "true-preview"


public class Main {
  void test() {
    ((Runnable) () -> {}).ru<caret>n();
  }
}
