// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      int x;
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
}