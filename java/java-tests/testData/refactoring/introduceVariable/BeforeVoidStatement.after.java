class Test {
  void foo() {
      Object c = getObject();
      c.notify();
  }

  Object getObject() {return null;}
}