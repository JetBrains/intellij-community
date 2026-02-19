// "Replace with 'Math.min()' call" "true"
class Test {

  void test(int a, int b, String s) {
    int c = switch (s) {
      case "foo" -> {
          yield Math.min(b, a);
      }
    };
  }
}