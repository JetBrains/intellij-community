class ClassWithEnum {

  public static void foo() {
    testMethod(TestEnum.values());
  }

  private static void testMethod(TestEnum[] values) { }

  public enum TestEnum {
    ONE, TWO, THREE
  }
}