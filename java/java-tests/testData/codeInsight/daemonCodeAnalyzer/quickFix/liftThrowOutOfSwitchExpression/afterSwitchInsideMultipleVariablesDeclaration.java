// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      int a = 3;
      int b;
      int c = 4;
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
}