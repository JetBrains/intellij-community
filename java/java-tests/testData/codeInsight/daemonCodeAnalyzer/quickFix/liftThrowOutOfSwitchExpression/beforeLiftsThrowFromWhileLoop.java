// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              while (new java.util.Random().nextBoolean()) {
                  throw new RuntimeException("while");
              }
              throw new RuntimeException("default");
          }
      };
  }
}