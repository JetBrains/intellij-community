import java.util.List;

class Test1 {
  interface A {
    <T extends Comparable<T>> String foo(List<T> x);
  }

  interface B {
    <K extends Comparable<K>> CharSequence foo(List<K> x);
  }

  class X {
    <S extends A & B> void bar(S x) {
      x.foo(null);
    }
  } 
}

class Test2 {
  interface A {
    void foo();
  }

  interface B {
    void foo();
  }

  abstract class X implements A, B {
    {
      foo();
    }
  }
}