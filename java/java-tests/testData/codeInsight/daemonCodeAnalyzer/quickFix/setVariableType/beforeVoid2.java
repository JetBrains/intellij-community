// "Set variable type to 'Void'" "false"
class Demo {
  void test() {
    var<caret> s = null;
    s = foo();
  }

  void foo() {}
}
