class Foo {
  public static void f<caret>oo() {}

  void m() {
    class FooExt extends Foo {
      {
        foo();
      }
    }

    class FooExt1 extends Foo {
      {
        foo();
      }
    }
  }
}