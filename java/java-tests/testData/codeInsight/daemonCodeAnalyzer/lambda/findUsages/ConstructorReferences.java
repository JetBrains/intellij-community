class Foo {
  Foo() { this(() -> 4); }

  Foo(I i) {}
}

interface <caret>I {
  int foo();
}