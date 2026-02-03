// "Fold expression into 'String.join'" "true-preview"
class Test {
  void test(String a, String b, String c, String d) {
    String result = String.join(",", a, b, c, d) + ",";
  }
}