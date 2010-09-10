public class A {
  private class <caret>B {
      public B() {
      }
  }

  static void m(A a) {
    B b = new A().new B();
    B b1 = a.new B();
    B b2 = new B();
    B b3 = getA().new B();
  }

  static A getA(){return null;}


}
