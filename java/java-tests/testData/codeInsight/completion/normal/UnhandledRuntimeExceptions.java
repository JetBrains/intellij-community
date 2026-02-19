class X{
  class CheckedException1 extends RuntimeException {
  }
  class CheckedException2 extends RuntimeException {
  }
  public void test() {
    try {
      method1();
      throw new CheckedException2();
    } c<caret>
  }

  private void method1() throws CheckedException1{

  }
}
