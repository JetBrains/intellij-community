class Foo {
  Foo() { this(() -> 4); }

  Foo(I i) {}
}

enum SomeEnum {
  FOO(() -> 5);

  SomeEnum(I i) {
  }
}

interface <caret>I {
  int foo();
}