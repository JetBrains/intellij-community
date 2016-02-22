
class Test1 {
  interface A<T> {
    void foo(T x);
    default void foo(String x) { }
  }

  class <error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(T)' in 'A'">C</error> implements A<String> { }
  abstract class <error descr="Test1.D inherits abstract and default for foo(String) from types Test1.A and Test1.A">D</error> implements A<String> {}
  interface <error descr="Test1.E inherits abstract and default for foo(String) from types Test1.A and Test1.A">E</error> extends A<String> {}
}

class Test2 {
  interface A {
    default void foo(String x) { }
  }

  interface B<T> extends A {
    void foo(T x);
  }

  <error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(T)' in 'B'">class <error descr="Class 'C' must either be declared abstract or implement abstract method 'foo(T)' in 'B'">C</error> implements B<String></error> { }
  abstract class D implements B<String> {}
  interface E extends B<String> {}
}

class Test3 {
  interface A<T> {
    default void foo(T x) {}
    default void foo(String x) { }
  }

  class <error descr="Test3.C inherits unrelated defaults for foo(T) from types Test3.A and Test3.A">C</error> implements A<String> { }
  abstract class <error descr="Test3.D inherits unrelated defaults for foo(T) from types Test3.A and Test3.A">D</error> implements A<String> {}
  interface <error descr="Test3.E inherits unrelated defaults for foo(T) from types Test3.A and Test3.A">E</error> extends A<String> {}
}

class Test4 {
  interface A {
    default void foo(String x) { }
  }

  interface B<T> extends A {
    default void foo(T x) {}
  }

  class C implements B<String> { }
  abstract class D implements B<String> {}
  interface E extends B<String> {}
}