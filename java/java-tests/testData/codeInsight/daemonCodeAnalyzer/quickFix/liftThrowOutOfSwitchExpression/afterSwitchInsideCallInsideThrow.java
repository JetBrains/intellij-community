// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      a(1);
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  Exception newException(int x, int y, int z) {
      return new RuntimeException();
  }
  void a(int x) {}
  void b(int x) {}
}