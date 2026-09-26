// "Simplify 'arg.isEmpty()' to false" "false"
class Test {
  void test() {
    String arg = "12";
    Boolean result = arg != null && (<caret>arg.isEmpty() ? true : null);
  }
}
