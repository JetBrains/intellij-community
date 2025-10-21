// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      a(1);
      b(2);
      throw <caret>switch (param) {
          default -> new RuntimeException("default");
      };
  }
  void parentMethod(int x, int y, int z) {}
  int nestedMethod(int x, int y, int z) {}
  void a(int x) {}
  void b(int x) {}
  void c(int x) {}
  void d(int x) {}
}