// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  int a() {return 0;}
}