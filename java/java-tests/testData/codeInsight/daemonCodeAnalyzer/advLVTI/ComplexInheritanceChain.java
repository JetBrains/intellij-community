class MyTest {
  interface BasicResult {}
  interface BasicSet<R extends BasicResult> {}
  interface BasicSession<R extends BasicResult, RS extends BasicSet<R>> {}

  interface Result<R extends BasicResult, S extends BasicSession<R, ?>> {}

  static Result<?, ?> create() {
    return null;
  }

  public void test() {
    var x = create();
  }
}