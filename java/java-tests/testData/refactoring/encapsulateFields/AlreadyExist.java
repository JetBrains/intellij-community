class Test {
  int i;
  int getI() {
    return i;
  }

  void foo() {
    i++;
    System.out.println(getI());

    Test t;
    i = t.i;
  }
}
