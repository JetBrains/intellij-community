class Foo {
    <T> T f() {
    }

    {
      this.<String><caret>f();
    }
}