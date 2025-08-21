// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      methodA(1);
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  void parentMethod(int x, int y, int z) {}
  void methodA(int x) {}
  void methodB(int x) {}
}