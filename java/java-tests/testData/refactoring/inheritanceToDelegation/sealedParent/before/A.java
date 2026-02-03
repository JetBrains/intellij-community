sealed class A permits B {
  void doSmth() {}
}

non-sealed class B extends A {
  void doAnother() {
    super.doSmth();
  }
}