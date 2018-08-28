// "Fold expression into 'String.join'" "true"
class Test {
  String  foo(String a, String b, String c) {
    String s = a + <caret>'_' + b + '_' + c ;
    return s;
  }
}