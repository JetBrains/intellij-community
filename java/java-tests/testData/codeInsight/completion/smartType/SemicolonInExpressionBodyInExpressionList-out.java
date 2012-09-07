class Test {
  public void foo() {
    new Thread(() -> notify());
  }
}
