class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
      throw new RuntimeException();
    } catch (CheckedException1 e)<caret>
  }

  private void method1() throws CheckedException1{

  }
}
