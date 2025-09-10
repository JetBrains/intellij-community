// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(Three param) {
      int x = a() + <caret>switch (param) {
          default -> throw new RuntimeException("default");
      } + b();
  }
  int a() {return 0;}
  int b() {return 0;}
}