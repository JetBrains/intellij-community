class A {
  void foo () {
    new Runnable () {
      public synchronized <caret>
    };
  }
}