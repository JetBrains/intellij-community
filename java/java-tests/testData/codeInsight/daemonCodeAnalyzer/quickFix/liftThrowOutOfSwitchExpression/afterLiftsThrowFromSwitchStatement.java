// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param, boolean b) {
      throw <caret>switch (param) {
          case 1:
              if (b) {
                  yield new RuntimeException("one");
              }
          default:
              yield new RuntimeException("default");
      };
  }
}