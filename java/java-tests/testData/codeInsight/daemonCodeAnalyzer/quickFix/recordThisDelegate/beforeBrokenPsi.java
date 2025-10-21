// "Delegate to canonical constructor" "false"
record Foo(int a, String b) {
  Foo<caret>(String s, int i) {
    b = s
    a = i;
    doSomething();
  }

  private void doSomething() {

  }
}