// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  enum Two {
    ONE, TWO;
  }
  int bar(Two param) {
      throw <caret>switch (param) {
          case ONE -> {
              yield new RuntimeException("1");
          }
          case TWO -> {
              yield new RuntimeException("2");
          }
          default -> {
              yield new RuntimeException("default");
          }
      };
  }
}