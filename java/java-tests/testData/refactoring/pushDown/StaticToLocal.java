class Foo {
  public static void f<caret>oo() {}

  void m() {
    class FooExt extends Foo { }
  }
}