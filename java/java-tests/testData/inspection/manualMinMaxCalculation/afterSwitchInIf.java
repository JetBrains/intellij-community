// "Replace with 'Math.min'" "true"
class Test {

  void test(int a, int b, String s) {
    int c = switch (s) {
      case "foo" -> {
          break Math.min(b, a);
      }
    };
  }
}