enum TestEnum {
  ONE("te<caret>stString");

  TestEnum(String str) {
  }

  private static class Constants {
    public static final String FOO = "";
  }
}