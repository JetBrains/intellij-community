class Foo {
  Foo myFoo;

  Foo foo(Foo foo) {
  }

  {
    myFoo = foo(myFoo)<caret>
  }

}