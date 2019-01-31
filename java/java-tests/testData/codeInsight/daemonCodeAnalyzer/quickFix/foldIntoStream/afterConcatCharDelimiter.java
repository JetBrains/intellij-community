// "Fold expression into 'String.join'" "true"
class Test {
  String  foo(String a, String b, String c) {
    String s = String.join("_", a, b, c);
    return s;
  }
}