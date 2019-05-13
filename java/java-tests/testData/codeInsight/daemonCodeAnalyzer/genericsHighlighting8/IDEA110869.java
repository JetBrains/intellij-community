class A {
}

abstract class B {
  public <T extends A> T getA(Class<T> aClass) {
    return null;
  }

  void foo(Class<?> aClass) {
    A a = getA<error descr="'getA(java.lang.Class<T>)' in 'B' cannot be applied to '(java.lang.Class<capture<?>>)'">(aClass)</error>;
  }
}
