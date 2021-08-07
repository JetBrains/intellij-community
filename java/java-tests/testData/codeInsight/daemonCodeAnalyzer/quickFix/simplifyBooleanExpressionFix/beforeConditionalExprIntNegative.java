// "Simplify 'arg.isEmpty()' to false" "false"
class Test {
  void test() {
    String arg = "12";
    Integer result = 2 * (<caret>arg.isEmpty() ? 1 : null);
  }
}
