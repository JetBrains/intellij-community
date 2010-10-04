public class A {
  class <caret>B {
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