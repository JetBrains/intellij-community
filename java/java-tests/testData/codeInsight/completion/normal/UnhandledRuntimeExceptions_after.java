class X{
  class CheckedException1 extends RuntimeException {
  }
  class CheckedException2 extends RuntimeException {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } catch (CheckedException1 e) {
        <selection>throw new RuntimeException(e);</selection><caret>
    }
  }

  private void method1() throws CheckedException1{

  }
}
