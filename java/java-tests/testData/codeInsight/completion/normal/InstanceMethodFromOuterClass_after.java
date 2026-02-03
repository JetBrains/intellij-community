class A {
  void foo(int x){}
  class Inner {
    void foo(){}

    void test() {
      A.this.foo(<caret>);
    }
  }
}