class EnumWithField {

  public static void foo() {
    testMethod(new TestEnum[]{TestE<caret>num.ONE, TestEnum.TWO, TestEnum.THREE});
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE(1), TWO(2), THREE(3);

    private final int levelCode;

    TestEnum(int levelCode) {
      this.levelCode = levelCode;
    }
  }
}