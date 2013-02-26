class Test<X> {

    interface I {
      void _();
    }

    

    void test() {
      <error descr="Incompatible types. Found: '<method reference>', required: 'Test.I'">I i1 = Test<String>::foo;</error>
      I i2 = Test::foo;
    }

    static void foo() { };
}
