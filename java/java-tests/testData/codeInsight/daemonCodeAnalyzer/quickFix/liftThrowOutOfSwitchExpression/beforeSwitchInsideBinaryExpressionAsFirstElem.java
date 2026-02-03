// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  int bar(int param) {
      return <caret>switch (param) {
          default -> throw new RuntimeException("default");
      } + a();
  }
  int a() {return 0;}
}