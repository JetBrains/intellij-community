// "Fix closing parenthesis placement" "true-preview"
public class Example {
  String foo(String s, boolean b) {
    return s;
  }

  String bar(String s) {
    return s;
  }

  void test() {
    foo(bar("hello"), true);
  }
}