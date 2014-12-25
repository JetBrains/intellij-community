class Test {
  interface I {
    String foo();
  }
  public void foo(int ii) {
    I r = () -> {
      <selection>return "42";</selection>
    };
  }
}
