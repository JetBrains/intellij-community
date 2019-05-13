package p;

class A {
    public static class B {
      public static B create() {
        return new B();
      }
    }
}

class U {
  void m(A a) {
    A.B b = a.new B(); 
  }
}