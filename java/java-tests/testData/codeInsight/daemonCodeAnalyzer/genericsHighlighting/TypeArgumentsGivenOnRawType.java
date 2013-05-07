import java.util.List;
class A<D> {
  abstract class C<S> {
    <T extends A> void foo(T.C<Integer> x) {
      Integer bar = x.bar();
    }

    <T extends A> void foo1(A.C<error descr="Type arguments given on a raw type"><Integer></error> x) {
      Integer bar = x.bar();
    }

    <T extends A> void foo2(A<String>.C<Integer> x) {
      Integer bar = x.bar();
    }

    abstract S bar();
  }
}

class A1 {
  abstract class C<S> {
    <T extends A1> void foo(T.C<Integer> x) {
      Integer bar = x.bar();
    }

    <T extends A1> void foo1(A1.C<Integer> x) {
      Integer bar = x.bar();
    }

    abstract S bar();
  }
}

interface Builder<T> {
    T build();
}

interface Test<D extends Test<D, X>, X> {
    static interface TestBuilder<D extends Test<D, X>, X> extends Builder<D> {}
}

interface Algorithm<T, B extends Builder<T>> {}

class SelectFromVariableType<X, T extends Test<T, X>>
        implements Algorithm<T, <error descr="Cannot select from a type parameter">T</error>.TestBuilder<T, X>> {

    List<T.TestBuilder<T, X>> b;
    T.TestBuilder<T, X> b1;
}
