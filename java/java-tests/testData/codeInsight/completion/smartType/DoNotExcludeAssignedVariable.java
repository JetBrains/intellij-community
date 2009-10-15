class Foo {
  Foo myFoo;

  Foo foo(Foo foo) {
  }

  {
    myFoo = foo(m<caret>)
  }

}