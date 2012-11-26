enum TestEnum {
  ONE(Constants1.xxx);

  TestEnum(String str) {
  }

    private static class Constants1 {
        public static final String xxx = "testString";
    }

    private class Constants {
    void foo(){}
  }
}