public class A {
  private static class B {
      public B() {
      }
  }

  static void m(A a) {
    B b = new B();
    B b1 = new B();
    B b2 = new B();
    B b3 = new B();
  }

  static A getA(){return null;}


}
