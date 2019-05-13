class Foo {
}

class FooFactory {
  static Foo createFoo() {}
}

class XXX {
  {
    Foo f = FooFactory.createFoo();<caret>
  }
}