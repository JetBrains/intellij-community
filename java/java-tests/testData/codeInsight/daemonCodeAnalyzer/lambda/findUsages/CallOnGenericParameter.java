interface I {
  int a();
}

class A<Z> {
  void foo(I i);
}

class C<X, Y, SR extends A<Y>> {
  void bar(SR t) {
    t.foo(() -> 1);
  }
}