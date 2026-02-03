// "Delegate to canonical constructor" "true-preview"
record Foo() {
  Foo<caret>(int i, String s) {
    doSomething();
  }

  private void doSomething() {

  }
}