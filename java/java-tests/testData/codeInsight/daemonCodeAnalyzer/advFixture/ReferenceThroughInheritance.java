package test.test;
import test.A;
import test.test.C.D.E;

class C extends A {
  public static class D extends B {
    public static class E {}
  }
  public void c(E e) {}
}