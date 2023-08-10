// "Fold expression into 'String.join'" "true-preview"
class Test {
  void test(String a, String b, String c, String d) {
    String result = a + "," + b + ((",")) + c + ","<caret> + d + ",";
  }
}