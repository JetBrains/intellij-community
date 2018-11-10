class A {
}

class B extends A {
    class Inner {
      void m() {
        B.super.toString();
        A a = B.this;
      }
    }
}