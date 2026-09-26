// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      int x;
      x = <caret>switch (param) {
          default -> throw new RuntimeException("default");
      };
  }
}