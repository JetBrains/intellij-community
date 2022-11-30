// "Add missing nested patterns" "true-preview"
class Main {
  void foo(Object obj) {
    switch (obj) {
      case Point(/*blah blah blah*/double x, double y, double z) -> {}
      default -> {}
    }
  }

  record Point(double x, double y, double z) {}
}
