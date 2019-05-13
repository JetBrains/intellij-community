class C {
  void test() {
    {
      accept(<error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> System.out.println(_));
      accept((int <error descr="Use of '_' as a lambda parameter name is not allowed">_</error>) -> System.out.println(_));
    }
  }

  interface I { void f(int i); }
  void accept(I i) { i.f(42); }
}
