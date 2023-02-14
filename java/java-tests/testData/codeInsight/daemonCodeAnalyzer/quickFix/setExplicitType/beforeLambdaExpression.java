// "Set variable type to '<lambda expression>'" "false"
class Demo {
  void test() {
    var<caret> s = () -> {};
  }
}
