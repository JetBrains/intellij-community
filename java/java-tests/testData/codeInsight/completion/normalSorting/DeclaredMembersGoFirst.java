class Foo {
  void fromSuper() {}
  void overridden() {}
}

class FooImpl extends Foo {
  void overridden() {}
  void fromThis() {}
}

class Bar {
    {
        new FooImpl().<caret>
    }
}
