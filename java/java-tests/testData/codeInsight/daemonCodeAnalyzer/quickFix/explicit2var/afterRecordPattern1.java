// "Replace explicit type with 'var'" "true-preview"
class Main {
  record Point(int x, int y) {}

  void foo(Object obj) {
    if (obj instanceof Point(@Anno var x, int y)) {
    }
  }
}

@interface Anno {}
