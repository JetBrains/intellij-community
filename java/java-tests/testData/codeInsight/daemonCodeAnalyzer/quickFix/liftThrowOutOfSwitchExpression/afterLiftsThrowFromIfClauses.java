// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              if (true) {
                  yield new RuntimeException("then");
              } else {
                  yield new RuntimeException("else");
              }
          }
      };
  }
}