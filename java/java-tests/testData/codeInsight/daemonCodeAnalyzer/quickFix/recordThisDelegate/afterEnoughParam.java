// "Delegate to canonical constructor" "true-preview"
record Foo(int a, String b) {
  Foo(String s, int i) {
      this(i, s);
      doSomething();
  }

  private void doSomething() {

  }
}