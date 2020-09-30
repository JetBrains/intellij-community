class Test {
  private final int <caret>myA;

  Test(int a) {
    myA = a * 1;
  }

  void test() {
    System.out.println(myA);
  }
}