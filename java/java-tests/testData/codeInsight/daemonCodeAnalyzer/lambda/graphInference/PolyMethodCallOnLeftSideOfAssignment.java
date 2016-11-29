class Test {

  public void myMethod()
  {
    foo ( <error descr="Variable expected">bar("")</error> = "");
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
