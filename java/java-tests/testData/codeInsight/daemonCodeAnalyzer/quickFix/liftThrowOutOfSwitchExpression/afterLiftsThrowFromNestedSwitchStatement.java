// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              switch (5) {
                  case 1:
                      yield new RuntimeException("one");
                  default:
                      yield new RuntimeException("default");
              }
          }
      };
  }
}