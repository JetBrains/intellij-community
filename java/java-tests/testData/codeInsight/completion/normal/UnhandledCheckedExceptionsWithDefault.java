class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  class CheckedException3 extends Exception {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
      throw new CheckedException3();
    } c<caret>
  }

  private void method1() throws CheckedException1{

  }
}
