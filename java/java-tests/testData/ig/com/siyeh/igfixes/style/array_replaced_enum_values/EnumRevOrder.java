class EnumRevOrder {

  public static void foo() {
    testMethod(new TestEnum[]<caret>{TestEnum.THREE, TestEnum.TWO, TestEnum.ONE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE;
  }
}