// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      int a = 3, b, c = 4, d = <caret>switch (param) {
          default -> throw new RuntimeException("default");
      }, e = 5;
  }
}