class Test {
  void f(Class... classes) {
  }

  void g() {
    f(((Class[])null));
    f(((Class)null));
    f(((Class)null),
      ((Class)null));
  }
}