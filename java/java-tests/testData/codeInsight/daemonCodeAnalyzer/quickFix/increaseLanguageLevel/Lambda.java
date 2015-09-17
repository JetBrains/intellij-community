public class Lambda {
  void m() {
    Runnable r = <caret>() -> x();
  }

  void x() {}
}