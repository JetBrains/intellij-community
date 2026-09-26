// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              while (new java.util.Random().nextBoolean()) {
                  yield new RuntimeException("while");
              }
              yield new RuntimeException("default");
          }
      };
  }
}