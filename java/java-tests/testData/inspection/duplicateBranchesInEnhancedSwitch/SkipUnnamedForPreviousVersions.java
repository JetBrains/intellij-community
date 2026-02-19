// "Merge with 'case R()'" "false"
class Test {
  record R() {
  }

  record S() {
  }

  void foo(Object obj) {
    switch (obj) {
      case R() -> System.out.println(42);
      case S() -> <caret>System.out.println(42);
      default -> {
      }
    }
  }
}
