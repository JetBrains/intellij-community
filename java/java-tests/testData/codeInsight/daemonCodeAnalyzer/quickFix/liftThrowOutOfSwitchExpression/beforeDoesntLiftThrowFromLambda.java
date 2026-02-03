// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              Runnable runnable = () -> {
                  throw new RuntimeException("runnable");
              };
              runnable.run();
              throw new RuntimeException("message");
          }
      };
  }
}