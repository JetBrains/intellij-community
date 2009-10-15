class Foo {
  Foo[] myFoos;

  Foo(Foo[] foos) {
    myFoos = true ? foos : <caret>
  }
}
