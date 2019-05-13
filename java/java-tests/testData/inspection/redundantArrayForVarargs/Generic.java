
class Generic {
  class A {}
  class B extends A {}

  void f() {
    B b = new B();
    C<A> l = asC(new A[]{b});
    A a = new A();
    C<A> m = asC(<warning descr="Redundant array creation for calling varargs method">new A[]{a}</warning>);
  }

  public static <T> C<T> asC(T... ts) {
    return null;
  }

  class C<T> {}

  void m() {
    System.out.println(String.format("%s %s", <warning descr="Redundant array creation for calling varargs method">new Object[] {"Z", "X"}</warning>));
  }
}
