public class A {
  static class B {
      public B() {
      }
  }
}

class C {
  void foo() {
    new A().new B() {
      public String toString() {
        return super.toString();
      }
    }.toString();
  }
}