
class Test {
  class A {}
  class B extends A {}

  void f() {
    B b = new B();
    C<A> l = asC(new A[]{b});
    A a = new A();
    C<A> m = asC(new A[]{a});
  }

  public static <T> C<T> asC(T... ts) {
    return null;
  }

  class C<T> {}
}
