// "Set variable type to '<lambda parameter>'" "false"
class Demo {
  void test() {
    Object x = a -> {
      var<caret> y = a;
    };
  }
}
