// "Replace with qualifier" "true"

class Test {
  void f(Runnable runnable) {
    Runnable r = () -> runna<caret>ble.run();
  }
}