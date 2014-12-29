class Test {
  public <T> T f() {
    return null;
  }

  public Object foo() {
    return true ? new String[]{f()} : new String[][]{f()};
  }
}
