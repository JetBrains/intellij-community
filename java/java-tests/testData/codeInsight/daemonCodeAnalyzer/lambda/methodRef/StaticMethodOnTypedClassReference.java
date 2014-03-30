class Test<X> {

    interface I {
      void _();
    }

    

    void test() {
      I i1 = <error descr="Parameterized qualifier on static method reference">Test<String>::foo</error>;
      I i2 = Test::foo;
    }

    static void foo() { };
}
