// "Delegate to canonical constructor" "true-preview"
record Foo(int a, String b) {
  Foo<caret>(String s, int i) {
    b = s;
    a = i;
    doSomething();
  }

  private void doSomething() {

  }
}