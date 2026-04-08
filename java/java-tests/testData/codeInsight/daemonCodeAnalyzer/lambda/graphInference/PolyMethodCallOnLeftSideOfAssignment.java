class Test {

  public void myMethod()
  {
    foo ( <error descr="Reference to variable expected on left-hand side of assignment">bar("")</error> = "");
  }

  private <T> T foo(final T bar) {
    return null;
  }
  private <T> T foo(final String bar) {
    return null;
  }

  private <S> S bar(final String s) {
    return null;
  }
}
