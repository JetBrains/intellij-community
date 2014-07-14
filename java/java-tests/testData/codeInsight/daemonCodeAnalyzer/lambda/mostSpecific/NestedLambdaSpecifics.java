class Test {

  interface A<T> {
    T a();
  }

  interface B<T> {
    T b();
  }

  private void m(A<Integer> <warning descr="Parameter 'a' is never used">a</warning>) { }
  private void <warning descr="Private method 'm(Test.B<java.lang.String>)' is never used">m</warning>(B<String> <warning descr="Parameter 'b' is never used">b</warning>) { }

  {
    m((() -> 42));
    m(true ? () -> 42 : () -> 42);
    m(true ? null : (() -> 42));
  }
}