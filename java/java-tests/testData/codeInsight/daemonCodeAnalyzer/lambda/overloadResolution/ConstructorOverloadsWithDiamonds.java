class Test {
  static class C<U> {
    U u;
    C(C<U> other) {
      u = other.u;
    }

    C(U u) {
      this.u = u;
    }
  }

  static <U> C<U> foo(C<U> c) { return new C<U>(c); }

  {
    C<String> c = foo(new C<>(foo(new C<>(foo(new C<>(foo(new C<>(foo(null)))))))));
  }
}