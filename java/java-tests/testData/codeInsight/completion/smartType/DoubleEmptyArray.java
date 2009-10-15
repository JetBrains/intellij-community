class Foo {
  public static final Foo[] EMPTY = new Foo[0];
}

class Bar {

  Foo[] bar() {}

  Foo[] foo() {
    return <caret>;
  }
}