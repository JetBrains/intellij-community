class InnerEnum {

  public static void foo() {
    testMethod(new TestEnum.Inner[]{TestEnum.Inn<caret>er.FIVE, TestEnum.Inner.SIX, TestEnum.Inner.SEVEN});
  }

  private static void testMethod(TestEnum.Inner[] values) {
  }

  public enum TestEnum {
    ONE, TWO, THREE;

    enum Inner {FIVE, SIX, SEVEN}
  }
}