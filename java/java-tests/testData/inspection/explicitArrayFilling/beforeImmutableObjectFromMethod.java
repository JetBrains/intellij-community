// "Replace loop with 'Arrays.fill()' method call" "true"

class Test {

  void test() {
    final String[] arr = new String[2];
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = getString();
    }
  }

  private static String getString() {
    return "foo";
  }
}