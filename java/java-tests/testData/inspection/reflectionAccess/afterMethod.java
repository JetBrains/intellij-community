// "Use 'getDeclaredMethod()'" "true"
class X {
  void test() {
    String.class.getDeclaredMethod("checkBounds", byte[].class, int.class, int.class);
  }
}