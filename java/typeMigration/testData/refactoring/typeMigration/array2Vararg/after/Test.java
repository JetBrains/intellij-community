class Test {

  void doSomething(CharSequence... args) { /* ... */
  }

  void x() {
    doSomething("foo");
    doSomething("one", "two");
  }
}