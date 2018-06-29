class Test {
  private String <flown111>s;
  private String s2;

  void test(String <caret>s, String s2) {
    this.s = <flown11>requireNonNull(<flown1>s);
    this.s2 = requireNonNull(s2);
  }

  String <flown11111>foo() {
    return <flown1111>s;
  }

  @org.jetbrains.annotations.Contract("null -> fail; !null -> param1")
  public static native <T> T requireNonNull(T <flown12>obj);
}