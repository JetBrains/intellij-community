class Test {
  private final int <caret>myA;

  Test(int a) {
    myA = 1;
  }

  void test() {
    System.out.println(myA);
  }
}