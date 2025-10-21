// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              Runnable runnable = () -> {
                  throw new RuntimeException("runnable");
              };
              runnable.run();
              yield new RuntimeException("message");
          }
      };
  }
}