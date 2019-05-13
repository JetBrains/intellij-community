class Test {
  void method(ThreadLocal<? super String> l) {
    l.substring(0);
  }
}