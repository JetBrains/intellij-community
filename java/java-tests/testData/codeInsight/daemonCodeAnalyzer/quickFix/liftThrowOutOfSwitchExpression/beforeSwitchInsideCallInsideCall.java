// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
    parentMethod(
      a(1),
      nestedMethod(
        b(2),
        <caret>switch (param) { default -> throw new RuntimeException("default"); },
        c(3)
      ),
      d(4)
    );
  }
  void parentMethod(int x, int y, int z) {}
  int nestedMethod(int x, int y, int z) {}
  void a(int x) {}
  void b(int x) {}
  void c(int x) {}
  void d(int x) {}
}