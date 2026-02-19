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
      Object x1 = x;
      consume(x1);
    System.out.println(x1.myMegaMethod());
  }
}