// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  enum Two {
    ONE, TWO;
  }
  int bar(Two param) {
      return <caret>switch (param) {
          case ONE -> {
            throw new RuntimeException("1");
          }
          case TWO -> {
            throw new RuntimeException("2");
          }
          default -> {
            throw new RuntimeException("default");
          }
      };
  }
}