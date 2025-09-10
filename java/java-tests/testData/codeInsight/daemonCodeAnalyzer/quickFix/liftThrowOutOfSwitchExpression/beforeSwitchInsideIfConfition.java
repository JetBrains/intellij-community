// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
    if (<caret>switch (param) { default -> throw new RuntimeException("default"); }) {
      methodA(1);
    } else {
      methodB(2);
    }
  }
  void methodA(int x) {}
  void methodB(int x) {}
}