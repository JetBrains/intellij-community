class Test {
  interface B1 extends B2 {
    void baseMethod();
  }
  interface B2 extends B3 {}
  interface B3 extends B1 {}

  long test(Object obj) {
    if (obj instanceof B1 || obj instanceof B2 || obj instanceof B3) {
      obj.baseMe<caret>
    }
    return -1;
  }
}