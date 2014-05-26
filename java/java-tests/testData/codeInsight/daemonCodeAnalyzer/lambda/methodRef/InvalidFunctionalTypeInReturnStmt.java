class Java8 {
  public void test() {
  }

  private int m() {
    return <error descr="int is not a functional interface">Java8::test</error>;
  }
}