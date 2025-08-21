// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
    parentMethod(
      methodA(1),
      <caret>switch (param) { default -> throw new RuntimeException("default"); },
      methodA(2)
    );
  }
  void parentMethod(int x, int y, int z) {}
  void methodA(int x) {}
  void methodB(int x) {}
}