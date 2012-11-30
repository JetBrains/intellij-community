enum TestEnum {
  ONE(Constants.xxx);

  TestEnum(String str) {
  }

  private static class Constants {
    public static final String FOO = "";
      public static final String xxx = "testString";
  }
}