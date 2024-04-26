class X{
  class CheckedException extends Exception {
  }
  class CheckedException1 extends CheckedException {
  }
  class CheckedException2 extends CheckedException {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } catch(CheckedException e) {
    } catc<caret>
  }

  private void method1() throws CheckedException1{

  }
}
