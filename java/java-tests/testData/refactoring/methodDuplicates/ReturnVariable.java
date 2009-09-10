class ReturnVariable {
  String <caret>bar(String s) {
    s = s + "";
    return s;
  }

  void foo() {
    String s1 = "";
    s1 = s1 + "";
    System.out.println(s1);
  }
}