class X {
  interface Fn {
    void test(String s);
  }
  
  void test() {
    Fn fn = (v<caret>ar s) -> System.out.println(s);
  }
}