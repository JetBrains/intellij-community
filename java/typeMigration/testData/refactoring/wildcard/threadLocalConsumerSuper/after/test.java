class Test {
  void method(ThreadLocal<? super String> l) {
    l.get().substring(0);
  }
}