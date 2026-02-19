// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              int x = switch (5) {
                  case 1:
                      throw new RuntimeException("one");
                  case 2:
                      yield 3;
                  default:
                      throw new RuntimeException("default");
              };
              yield new RuntimeException("message");
          }
      };
  }
}