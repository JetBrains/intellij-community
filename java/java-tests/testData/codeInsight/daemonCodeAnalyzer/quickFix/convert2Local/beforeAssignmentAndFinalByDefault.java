// "Convert field to local variable in method 'getFoo1'" "true-preview"
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