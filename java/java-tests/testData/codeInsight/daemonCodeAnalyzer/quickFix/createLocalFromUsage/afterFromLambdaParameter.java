// "Create local variable 'a'" "true"
class C {
  void foo() {
    (s) -> {
        Object a = s;
    };
  }
}
