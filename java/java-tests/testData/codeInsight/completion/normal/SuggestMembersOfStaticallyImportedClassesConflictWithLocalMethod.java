import static foo.Foo.foo;

class Bar {
  {
    foo();
    ba<caret>x
  }

  void bar();
}
