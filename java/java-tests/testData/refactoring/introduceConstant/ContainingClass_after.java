class Test {

    public static final String xxx = StaticInner.STRING;

    public String getString() {
    return xxx;
  }

  static class StaticInner {
    public static String STRING = "aaaa";
  }
}