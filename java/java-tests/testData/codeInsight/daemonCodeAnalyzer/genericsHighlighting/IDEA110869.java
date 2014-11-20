class A {
}

abstract class B {
  public <T extends A> T getA(Class<T> aClass) {
    return null;
  }

  void foo(Class<?> aClass) {
    A a = <error descr="Inferred type 'capture<?>' for type parameter 'T' is not within its bound; should extend 'A'">getA(aClass)</error>;
  }
}