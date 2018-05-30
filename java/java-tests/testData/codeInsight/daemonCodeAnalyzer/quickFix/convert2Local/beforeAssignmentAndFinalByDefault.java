// "Convert to local" "true"
class Test {
  private int my<caret>Foo;

  int getFoo1() {
    while (true) myFoo = 1;
    return myFoo;
  }
}