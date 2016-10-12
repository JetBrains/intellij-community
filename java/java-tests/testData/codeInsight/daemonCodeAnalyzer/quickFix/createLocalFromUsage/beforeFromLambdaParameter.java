// "Create local variable 'a'" "true"
class C {
  void foo() {
    (s) -> {
      <caret>a = s;
    };
  }
}
