class Test {
  void method(ThreadLocal<? extends String> l) {
    l.get().substring(0);
  }
}