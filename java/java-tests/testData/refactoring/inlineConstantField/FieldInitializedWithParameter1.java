class Test {
  private final int <caret>myA;

  Test(int a) {
    myA = a * 1;
    System.out.println(myA);
  }
}