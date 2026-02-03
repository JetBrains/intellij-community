// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(Three param) {
      int x = <caret>switch (param) {
          default -> throw new RuntimeException("default");
      };
  }
}