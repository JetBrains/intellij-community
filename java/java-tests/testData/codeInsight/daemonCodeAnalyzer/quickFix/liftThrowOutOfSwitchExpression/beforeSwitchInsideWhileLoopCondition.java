// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      while (<caret>switch (param) { default -> throw new RuntimeException("default"); }) {
          methodA(1);
      }
  }
  void methodA(int x) {}
}