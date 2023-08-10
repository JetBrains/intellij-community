class Test {

  void doSomething(String[] args) { /* ... */
  }

  void x() {
    doSomething(new String[]{"foo"});
    doSomething(new String[]{"one", "two"});
  }
}