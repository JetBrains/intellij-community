// "Simplify 'arg.isEmpty()' to false" "true"
class Test {
  void test() {
    String arg = "12";
    Integer result = 2 * (<caret>arg.isEmpty() ? null : 1);
  }
}
