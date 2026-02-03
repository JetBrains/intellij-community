class ReturnExpression {
  int <caret>bar(String s) {
    s = s + "";
    return s.length();
  }

  void foo() {
    String s1 = "";
    s1 = s1 + "";
    System.out.println(s1);
  }
}