// "Add missing nested patterns" "true-preview"
class Main {
  void foo(Object obj) {
    switch (obj) {
      case Point(double x/*blah blah blah*/<caret>) -> {}
      default -> {}
    }
  }

  record Point(double x, double y, double z) {}
}
