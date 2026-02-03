class Test {
  interface A<<warning descr="Type parameter 'T' is never used">T</warning>> {}
  interface B<<warning descr="Type parameter 'T' is never used">T</warning>> {}

  static class BImpl<T> implements B<T> {
    BImpl() {}
  }

  static class Main<T>  {
    private void <warning descr="Private method 'supply(Test.A<T>)' is never used">supply</warning>(A<T> <warning descr="Parameter 'a' is never used">a</warning>) {}
    private void supply(B<T> <warning descr="Parameter 'b' is never used">b</warning>) {}

    public void test(boolean flag) {
      supply(flag ? new BImpl<>() : new BImpl<>());
    }
  }
}