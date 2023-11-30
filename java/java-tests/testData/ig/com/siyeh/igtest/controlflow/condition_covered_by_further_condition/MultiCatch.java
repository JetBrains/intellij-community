public class MultiCatch {
  static class E1 extends Exception {}
  static class E2 extends Exception {}
  static class E3 extends E2 {}

  void test() throws Exception {
    try {
      foo();
    }
    catch (E1 | E2 ex) {
      if(ex instanceof E1 || ex instanceof E3) {
        System.out.println();
      }
    }
  }

  native void foo() throws Exception;
}