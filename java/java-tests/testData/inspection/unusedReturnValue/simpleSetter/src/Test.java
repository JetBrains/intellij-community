class Test {
  private String myStr;

  public Test setStr(String str) {
    myStr = str;
    return this;
  }

  public static void main(String[] args) {
    final Test test = new Test();
    test.setStr("");
  }
}
