public enum TestEnum {
  A {
    @Override
    void publicMethod() {
      nonPrivateMethod();
    }
  };

  void nonPrivateMethod() {}

  void publicMethod() {}
}