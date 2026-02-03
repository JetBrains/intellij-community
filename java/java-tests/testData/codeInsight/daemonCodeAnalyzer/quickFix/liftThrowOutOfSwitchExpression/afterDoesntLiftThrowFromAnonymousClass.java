// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      throw <caret>switch (7) {
          default -> {
              new Runnable() {
                  public void run() {
                      throw new RuntimeException("runnable");
                  }
              }.run();
              yield new RuntimeException("message");
          }
      };
  }
}