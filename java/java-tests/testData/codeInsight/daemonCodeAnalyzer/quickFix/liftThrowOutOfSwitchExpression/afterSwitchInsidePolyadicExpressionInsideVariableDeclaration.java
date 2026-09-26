// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(Three param) {
      a();
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  int a() {return 0;}
  int b() {return 0;}
}