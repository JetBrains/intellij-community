class Foo {
  void foo() {
    int x = 0;
    int y = x = newMethod();
  }

    private int newMethod() {
        int x;
        return x = 1;
    }
}