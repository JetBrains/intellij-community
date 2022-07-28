// "Convert to local" "true-preview"
class Test {

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