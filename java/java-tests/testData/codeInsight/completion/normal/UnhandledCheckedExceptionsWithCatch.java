class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } catch(CheckedException2 e) {
    } catc<caret>
  }

  private void method1() throws CheckedException1{

  }
}