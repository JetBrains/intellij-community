// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              if (true) {
                  throw new RuntimeException("then");
              } else {
                  throw new RuntimeException("else");
              }
          }
      };
  }
}