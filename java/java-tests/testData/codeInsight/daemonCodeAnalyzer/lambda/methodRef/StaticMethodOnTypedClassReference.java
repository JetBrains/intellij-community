class Test<X> {
    interface I {
      void m();
    }

    void test() {
      I i1 = Test<error descr="Parameterized qualifier on static method reference"><String></error>::foo;
      I i2 = Test::foo;
    }

    static void foo() { };
}
