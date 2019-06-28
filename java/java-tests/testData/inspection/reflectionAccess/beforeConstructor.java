// "Use 'getDeclaredConstructor()'" "true"
class X {
  void test() {
    String.class.getConstructor(<caret>char[].class, boolean.class);
  }
}