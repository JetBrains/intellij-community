// "Convert to local" "true"
class Test {
  private int my<caret>Foo;

  int getFoo1() {
    myFoo = 1;
    myFoo = 5;
    return myFoo;
  }
}