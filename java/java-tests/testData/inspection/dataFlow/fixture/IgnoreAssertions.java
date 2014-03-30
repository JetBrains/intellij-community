class Foo {
  Object getObjectFromElsewhere() { return null; }
  void foo() {
    Object object = getObjectFromElsewhere();
    assert (object != null);
    if (object != null) {
      return;
    }
  }
}