// "Replace with 'Math.min()' call" "true"
class Test {

  void test(int a, int b, String s) {
    int c = switch (s) {
      case "foo" -> {
        if<caret>(b > a) yield a;
        else yield b;
      }
    };
  }
}