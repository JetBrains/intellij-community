class C {
  boolean fooInverted() {
    return true;
  }

  void bar() {
    boolean a, b;
    a = b = !fooInverted();
  }
}
