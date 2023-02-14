// "Simplify 'arg.isEmpty()' to false" "true-preview"
class Test {
  void test() {
    String arg = "12";
    String result = arg + (<caret>arg.isEmpty() ? 1 : null);
  }
}
