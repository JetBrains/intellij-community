// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      for (int i = 0; i < 10; i++) {
          int x = <caret>switch (param) { default -> throw new RuntimeException("default"); };
      }
  }
}