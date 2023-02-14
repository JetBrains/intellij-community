// "Replace explicit type with 'var'" "false"
class Main {
  record Point(int x, int y) {}

  void foo(Object obj) {
    if (obj instanceof Point((((int<caret> x))), int y)) {
    }
  }
}
