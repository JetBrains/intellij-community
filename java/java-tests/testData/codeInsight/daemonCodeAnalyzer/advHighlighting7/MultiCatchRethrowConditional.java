class SomeClass {
  static class Ex1 extends Exception {}
  static class Ex2 extends Exception {}

  native void run() throws Ex1, Ex2;

  void test() throws Exception {
    try {
      run();
    } catch (Ex1 | Ex2 e) {
      throw e.getCause() != null ? new RuntimeException() : e;
    }
  }
}