import foo.Outer;

class Foo {
  {
    new Outer.<caret>Inner() { };
  }
}

