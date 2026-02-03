class Test {
  void foo(Object result) {
    long h = result.hashCode();
  }
  
  void bar() {
    fo<caret>o(result);
  }
}