// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      return a() + <caret>switch (param) {
          default -> throw new RuntimeException("default");
      } + b();
  }
  int a() {return 0;}
  int b() {return 0;}
}