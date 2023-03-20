// "Create local variable 'a'" "true-preview"
class C {
  void foo() {
    (s) -> {
      <caret>a = s;
    };
  }
}
