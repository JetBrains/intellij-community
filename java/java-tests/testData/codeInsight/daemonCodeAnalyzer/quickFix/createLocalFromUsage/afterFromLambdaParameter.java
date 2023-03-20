// "Create local variable 'a'" "true-preview"
class C {
  void foo() {
    (s) -> {
        Object a = s;
    };
  }
}
