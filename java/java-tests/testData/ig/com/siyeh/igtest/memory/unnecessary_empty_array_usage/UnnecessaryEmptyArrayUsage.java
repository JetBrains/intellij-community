class A {
  public static final A[] ZERO_LENGTH = new A[0];
}
class B {
  public static final B[] EMPTY_INITIALIZER = {};
}
class C {
  public static final B[] WRONG_CLASS = {};
}
class D {
  private static final D[] PRIVATE = {};
}
class E {
  public static final E[] NON_EMPTY = {null};
}
class F {
  public final F[] NON_STATIC = {};
}
class Test {
  void test() {
    A[] as = <warning descr="Zero length array can be changed to constant">new A[0]</warning>;
    B[] bs = <warning descr="Zero length array can be changed to constant">new B[] {}</warning>;
    C[] cs = new C[0];
    D[] ds = new D[0];
    E[] es = new E[0];
    F[] fs = new F[0];
  }
}