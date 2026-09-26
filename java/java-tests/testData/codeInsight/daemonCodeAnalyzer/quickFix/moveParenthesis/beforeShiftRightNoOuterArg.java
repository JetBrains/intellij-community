// "Fix closing parenthesis placement" "false"
public class Example {
  String foo(String s, boolean b) {
    return s;
  }

  String bar(String s) {
    return s;
  }

  void test() {
    bar(foo(<caret>"hello"));
  }
}