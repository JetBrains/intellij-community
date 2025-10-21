// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              for (int i = 0; i < new java.util.Random().nextInt(); i++) {
                  yield new RuntimeException("for");
              }
              yield new RuntimeException("default");
          }
      };
  }
}