// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              {
                  throw new RuntimeException("then");
              }
          }
      };
  }
}