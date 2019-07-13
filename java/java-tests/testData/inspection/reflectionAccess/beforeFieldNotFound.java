// "Use 'getDeclaredField()'" "false"
class X {
  void test() {
    String.class.getField("<caret>hashhhh");
  }
}