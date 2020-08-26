class Test {
  private final int <caret>myA;

  Test() {
    class Local {}
    myA = new Local().hashCode();
  }

  void test() {
    System.out.println(myA);
  }
}