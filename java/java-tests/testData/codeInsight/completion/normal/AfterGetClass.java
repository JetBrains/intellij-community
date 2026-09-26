// IDEA-218260
class Foo {
  static void foo(Object bar) {
    if (!bar.getClass().equals(Foo.class)) {
      return;
    }
    bar.fo<caret>;
  }
  void foo() {
  }
}