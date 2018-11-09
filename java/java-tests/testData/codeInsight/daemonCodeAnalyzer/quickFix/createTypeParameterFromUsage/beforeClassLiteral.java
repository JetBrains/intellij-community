// "Create type parameter 'A'" "false"

class B {
  void foo() {
    java.lang.Class a = A<caret>.class;
  }
}