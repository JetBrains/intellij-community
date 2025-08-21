// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar() {
      return <caret>switch (7) {
          default -> {
              switch (5) {
                  case 1:
                      throw new RuntimeException("one");
                  default:
                      throw new RuntimeException("default");
              }
          }
      };
  }
}