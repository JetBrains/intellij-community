class Test {
  private int f;

  public void bar() {
    foo(() -> {f++;});
  }

  private void foo(Runnable r) {
  }
}
