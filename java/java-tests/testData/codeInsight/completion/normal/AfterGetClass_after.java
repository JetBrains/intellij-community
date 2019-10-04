// IDEA-218260
class Foo {
  static void foo(Object bar) {
    if (!bar.getClass().equals(Foo.class)) {
      return;
    }
    ((Foo) bar).foo();<caret>
  }
  void foo() {
  }
}