class Test {
  void m() {
    String <caret>s = "";
    try (s) {
      System.out.println(s);
    }
  }
}