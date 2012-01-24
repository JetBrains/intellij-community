public class Util {
  void foo(int a, int b) {
    Outer: for (int i = 0; i < 239; i++) {
      continue <caret>
    }
  }
}