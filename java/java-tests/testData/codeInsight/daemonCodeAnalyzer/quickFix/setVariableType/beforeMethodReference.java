// "Set variable type to '<method reference>'" "false"
class Demo {
  void test() {
    var<caret> s;
    s = this::foo;
  }
}
