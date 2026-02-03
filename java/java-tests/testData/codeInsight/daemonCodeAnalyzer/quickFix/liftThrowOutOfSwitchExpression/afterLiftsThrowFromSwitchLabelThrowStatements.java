// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  enum Three {
    ONE, TWO;
  }
  int bar(Three param) {
      throw <caret>switch (param) {
          case ONE -> new RuntimeException("1");
          case TWO -> new RuntimeException("2");
          default -> new RuntimeException("default");
      };
  }
}