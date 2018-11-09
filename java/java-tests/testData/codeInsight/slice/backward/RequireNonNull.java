class Test {

  private String <flown1>s;
  private String s2;

  void test(String <flown112111><flown1111>s, String s2) {
    this.s = <flown11>requireNonNull(<flown11211><flown111>s);
    this.s2 = requireNonNull(s2);
  }

  String foo() {
    return <caret>s;
  }

  public static <T> T requireNonNull(T <flown1121>obj) {
    if (obj == null)
      throw new NullPointerException();
    return <flown112>obj;
  }

}