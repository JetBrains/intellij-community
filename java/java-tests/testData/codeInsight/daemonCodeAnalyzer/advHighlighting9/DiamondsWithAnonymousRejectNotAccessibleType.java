
class B {
  private static class A {}

  public static class C extends A {}
  public static class D extends A {}
}

class Foo<E> {
  Foo(E e, E e1) {}

  {
    Foo foo = new Foo<<error descr="Cannot use ''<>'' with anonymous inner classes"></error>>(new B.C(), new B.D()) {};
  }
}