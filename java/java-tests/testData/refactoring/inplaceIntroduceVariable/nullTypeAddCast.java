public class NullTypeAddCast {
  static class MyMegaClass {
    int myMegaMethod() {
      return 0;
    }
  }

  interface MyInterface {}

  void consume(MyInterface intf) {}

  void test() {
    var x = foo;
    consume(x);
    System.out.println(<selection>x</selection>.myMegaMethod());
  }
}