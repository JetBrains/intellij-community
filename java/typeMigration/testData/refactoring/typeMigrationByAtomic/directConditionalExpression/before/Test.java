class Test {
  String s;

  void foo() {
    String s1 = s.length() > 2 ? s.substring(1) : s.trim();
  }
}