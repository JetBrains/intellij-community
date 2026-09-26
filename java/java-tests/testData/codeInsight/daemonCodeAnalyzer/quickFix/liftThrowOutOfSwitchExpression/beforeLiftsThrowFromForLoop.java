// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              for (int i = 0; i < new java.util.Random().nextInt(); i++) {
                  throw new RuntimeException("for");
              }
              throw new RuntimeException("default");
          }
      };
  }
}