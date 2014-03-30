class C {
  void test() {
    {
      I <warning descr="Use of '_' as an identifier might not be supported in releases after Java 8">_</warning> = new I() { public void f(int i) { } };
      accept(_);
    }

    {
      accept(<error descr="Use of '_' as a lambda parameter name is not allowed">_</error> -> System.out.println(_));
      accept((int <error descr="Use of '_' as a lambda parameter name is not allowed">_</error>) -> System.out.println(_));
    }
  }

  interface I { void f(int i); }
  void accept(I i) { i.f(42); }
}
