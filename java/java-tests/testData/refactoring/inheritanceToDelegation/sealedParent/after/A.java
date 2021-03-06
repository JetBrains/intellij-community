class A {
  void doSmth() {}
}

class B {
    public final A myDelegate = new A();

    void doAnother() {
    myDelegate.doSmth();
  }
}