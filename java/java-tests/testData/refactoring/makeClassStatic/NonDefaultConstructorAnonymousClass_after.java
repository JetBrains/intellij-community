public class A {
  static class B {
    B() {
    }
  }
}

class C {
  void foo() {
    new A.B() {
      public String toString() {
        return super.toString();
      }
    }.toString();
  }
}