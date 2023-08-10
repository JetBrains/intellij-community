// "Set variable type to '<method reference>'" "false"
class Demo {
  void test() {
    var<caret> s = this::foo;
  }
}
