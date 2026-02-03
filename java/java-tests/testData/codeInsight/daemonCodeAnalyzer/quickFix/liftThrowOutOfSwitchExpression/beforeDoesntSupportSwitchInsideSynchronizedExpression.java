// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  void bar(int param) {
      synchronized (<caret>switch (param) { default -> throw new RuntimeException("default"); }) {
      }
  }
}