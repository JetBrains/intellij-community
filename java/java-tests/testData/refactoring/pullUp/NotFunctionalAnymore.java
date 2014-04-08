interface I {
  void foo();
}

class IImpl implements I {
  public void foo(){}
  public void g<caret>et() {}

  {
    I i = () -> {};
  }
}