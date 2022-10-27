// "Create field 'a'" "true-preview"
class C {
  void foo() {
    if (true || <caret>a < 42) {}
  }
}