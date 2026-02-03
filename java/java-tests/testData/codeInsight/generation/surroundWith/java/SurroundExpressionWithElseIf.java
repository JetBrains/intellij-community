class Test {
  boolean foo() {
    return true;
  }
  
  void bar() {
    if (true) {
    }
    else <selection>foo()</selection>
  }
}