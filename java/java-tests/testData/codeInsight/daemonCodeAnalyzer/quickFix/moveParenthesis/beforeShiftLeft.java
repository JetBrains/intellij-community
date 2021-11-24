// "Fix closing parenthesis placement" "true"
public class Example {
  String foo(String s, boolean b) {
    return s;
  }

  String bar(String s) {
    return s;
  }

  void test() {
    foo(bar("hello", t<caret>rue));
  }
}