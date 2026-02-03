class X{
  class CheckedException1 extends Exception {
  }
  class CheckedException2 extends Exception {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } cat<caret>
  }

  private void method1() throws CheckedException1{

  }
}
