// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              {
                  yield new RuntimeException("then");
              }
          }
      };
  }
}