// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              class LocalClass {
                  public void fooBar() {
                      throw new RuntimeException("local classs");
                  }
              }
              throw new RuntimeException("message");
          }
      };
  }
}