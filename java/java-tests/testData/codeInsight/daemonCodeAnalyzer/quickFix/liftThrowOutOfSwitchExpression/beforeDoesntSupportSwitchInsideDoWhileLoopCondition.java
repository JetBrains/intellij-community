// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  void bar(int param) {
      do {
          methodA(1);
      } while (<caret>switch (param) { default -> throw new RuntimeException("default"); })
  }
  void methodA(int x) {}
}