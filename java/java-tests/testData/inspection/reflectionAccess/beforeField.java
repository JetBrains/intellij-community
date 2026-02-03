// "Use 'getDeclaredField()'" "true"
class X {
  void test() {
    String.class.getField("<caret>hash");
  }
}