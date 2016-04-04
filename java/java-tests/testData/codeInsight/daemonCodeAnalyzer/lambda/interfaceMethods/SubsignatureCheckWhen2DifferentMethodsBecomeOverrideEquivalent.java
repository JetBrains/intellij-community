
class Test1 {
  interface A {
    void foo(String x);
  }

  interface C<T> extends A {

    default void foo(T x) { }
  }

  class <error descr="Class 'D' must either be declared abstract or implement abstract method 'foo(String)' in 'A'">D</error> implements C<String> { }
  interface E extends C<String> { }
}

class Test2 {
  interface A {
    default void foo(String x) {}
  }

  interface C<T> extends A {

    void foo(T x);
  }

  <error descr="Class 'D' must either be declared abstract or implement abstract method 'foo(T)' in 'C'">class <error descr="Class 'D' must either be declared abstract or implement abstract method 'foo(T)' in 'C'">D</error> implements C<String></error> {}
  interface E extends C<String> {}
}

class Test3 {
  interface A {
    default void foo(String x) {}
  }

  interface C<T> extends A {

    default void foo(T x) {}
  }

  class D implements C<String> { }
  interface E extends C<String> { }
}

class Test4 {
  interface A {
    void foo(String x);
  }

  interface C<T> extends A {

    void foo(T x);
  }

  abstract class D implements C<String>, A { }
  interface E extends C<String>, A {}
}

class Test5 {
  interface A {
    void foo(String x);
  }

  abstract class B implements A { }

  interface C<T> extends A {
    default void foo(T x) { }
  }

  class <error descr="Class 'D' must either be declared abstract or implement abstract method 'foo(String)' in 'A'">D</error> extends B implements C<String> { }
}


class Test6 {
  interface A {
    default void foo(String s) { }
  }

  interface B extends A {}

  interface C<T> extends A {
    default void foo(T t) {}
  }
  abstract static class D implements C<String>, B { }
  static class E extends D {}
}