// "Use 'getDeclaredConstructor()'" "true"
class X {
  void test() {
    String.class.getDeclaredConstructor(char[].class, boolean.class);
  }
}