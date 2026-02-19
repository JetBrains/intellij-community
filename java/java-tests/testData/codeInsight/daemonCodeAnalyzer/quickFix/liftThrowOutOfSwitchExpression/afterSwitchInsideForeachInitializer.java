// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  void methodA(int x) {}
}