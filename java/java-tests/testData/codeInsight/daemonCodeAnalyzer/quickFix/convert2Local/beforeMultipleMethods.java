// "Convert to local" "true"
class Test {
  private int my<caret>Foo;

  int getFoo1() {
    myFoo//c1
      = 1;
    return myFoo;
  }

  int getFoo2() {
    myFoo = 2;
    return myFoo;
  }
}