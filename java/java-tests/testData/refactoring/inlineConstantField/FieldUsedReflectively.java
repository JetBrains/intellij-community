class Test {
  private static final int <caret>FIELD = 5;

  void test() {
    System.out.println(FIELD);
    System.out.println(Test.class.getDeclaredField("FIELD"));
  }
}