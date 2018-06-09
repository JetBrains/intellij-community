// "Convert to local" "true"
class Test {
  private int my<caret>Foo;

  int getFoo1(boolean f) {
    if (f) {
      myFoo = 1;
    }
    else {
      myFoo = 2;
    }
    return myFoo;
  }
}