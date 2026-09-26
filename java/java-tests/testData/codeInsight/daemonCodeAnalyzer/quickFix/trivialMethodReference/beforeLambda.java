// "Replace with qualifier" "true-preview"

class Test {
  void f(Runnable runnable) {
    Runnable r = () -> runna<caret>ble.run();
  }
}