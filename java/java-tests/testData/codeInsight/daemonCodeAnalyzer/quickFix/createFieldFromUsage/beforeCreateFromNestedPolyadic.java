// "Create field 'a'" "true"
class C {
  void foo() {
    if (true || <caret>a < 42) {}
  }
}