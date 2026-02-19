interface I {
  String f(String s);
}
class MyTest {
  void foo(I i) {}
  void bar(boolean b) {
    if (b) {
        String expr = "extract me";
        foo(s -> expr + s);
    }
  }
}