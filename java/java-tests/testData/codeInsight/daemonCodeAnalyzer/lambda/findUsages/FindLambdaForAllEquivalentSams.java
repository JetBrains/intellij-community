class C {
  void m(Foo f) {}

  {
    m(() -> {});
  }
}