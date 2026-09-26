// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(Three param) {
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
}