// "Use 'getDeclaredMethod()'" "true"
class X {
  void test() {
    String.class.getMethod("checkBounds<caret>", byte[].class, int.class, int.class);
  }
}