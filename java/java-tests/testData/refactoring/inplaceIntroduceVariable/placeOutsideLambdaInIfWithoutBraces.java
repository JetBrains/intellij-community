interface I {
  String f(String s);
}
class MyTest {
  void foo(I i) {}
  void bar(boolean b) {
    if (b) foo(s -> "extract<caret> me" + s);
  }
}