// "Delegate to canonical constructor" "true-preview"
record Foo() {
  Foo(int i, String s) {
      this();
      doSomething();
  }

  private void doSomething() {

  }
}