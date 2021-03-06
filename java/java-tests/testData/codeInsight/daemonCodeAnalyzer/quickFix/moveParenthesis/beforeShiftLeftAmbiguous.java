// "Fix closing parenthesis placement" "false"
public class Example {
  String foo(String s, boolean b, boolean c) {
    return s;
  }

  String foo(String s, boolean b) {
    return s;
  }

  String bar(String s) {
    return s;
  }
  
  String bar(String s, boolean b) {
    return s;
  }

  void test() {
    foo(bar("hello", true, <caret>true));
  }
}