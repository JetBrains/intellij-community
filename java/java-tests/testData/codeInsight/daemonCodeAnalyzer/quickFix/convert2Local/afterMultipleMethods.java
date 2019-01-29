// "Convert field to local variables in 2 methods" "true"
class Test {

    int getFoo1() {
        //c1
        int myFoo = 1;
    return myFoo;
  }

  int getFoo2() {
      int myFoo = 2;
    return myFoo;
  }
}