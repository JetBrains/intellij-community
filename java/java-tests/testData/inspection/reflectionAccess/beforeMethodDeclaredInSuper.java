// "Use 'getMethod()'" "true"
class X {
  void test() {
    String.class.getDeclaredMethod("<caret>getClass");
  }
}