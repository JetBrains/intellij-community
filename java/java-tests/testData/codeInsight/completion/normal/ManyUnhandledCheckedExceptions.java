class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  public void test() {
    try {
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      method1();
      throw new CheckedException2();
    } c<caret>
  }

  private void method1() throws CheckedException1{

  }
}
