// "Convert field to local variable in method 'getFoo1'" "true-preview"
class Test {

    int getFoo1(boolean f) {
        int myFoo;
        if (f) {
      myFoo = 1;
    }
    else {
      myFoo = 2;
    }
    return myFoo;
  }
}