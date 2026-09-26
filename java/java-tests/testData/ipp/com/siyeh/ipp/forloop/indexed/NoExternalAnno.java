class X {
  void test(String str) {
    <caret>for(String s : str.split("/")) {
      System.out.println(s);
    }
  }
}