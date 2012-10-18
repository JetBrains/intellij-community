class A<D> {
  abstract class C<S> {
    <T extends A> void foo(T.C<Integer> x) {
      Integer bar = x.bar();
    }

    <T extends A> void foo1(A.C<error descr="Type arguments given on a raw type"><Integer></error> x) {
      <error descr="Incompatible types. Found: 'java.lang.Object', required: 'java.lang.Integer'">Integer bar = x.bar();</error>
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