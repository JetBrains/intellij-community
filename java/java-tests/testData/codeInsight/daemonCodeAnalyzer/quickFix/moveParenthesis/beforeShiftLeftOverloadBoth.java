// "Fix closing parenthesis placement" "true"
public class Example {
  String foo(String s, boolean b) {
    return s;
  }

  String foo(String s, int i) {
    return s;
  }

  String bar(String s) {
    return s;
  }
  
  String bar(String s, boolean b1) {
    return s;
  }

  void test() {
    foo(bar("hello"<caret>, true, true));
  }
}