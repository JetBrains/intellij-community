// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      throw newException(
          a(1),
          <caret>switch (param) { default -> throw new RuntimeException("default"); },
          b(2)
      );
  }
  Exception newException(int x, int y, int z) {
      return new RuntimeException();
  }
  void a(int x) {}
  void b(int x) {}
}