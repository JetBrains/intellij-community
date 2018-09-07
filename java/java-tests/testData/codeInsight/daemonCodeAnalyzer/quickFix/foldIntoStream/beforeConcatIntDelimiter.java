// "Fold expression into 'String.join'" "false"
class Test {
  String  foo(String a, String b, String c) {
    String s = a + <caret>5 + b + 5 + c ;
    return s;
  }
}