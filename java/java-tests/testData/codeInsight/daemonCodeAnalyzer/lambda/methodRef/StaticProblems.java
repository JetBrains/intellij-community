class MyTest {
    interface I {
       String m(Foo f);
    }

    static class Foo<X> {
       String foo() { return null; }

       Foo<X> getFoo() { return this; }

       static void test() {
          I i1 = Foo.<error descr="Non-static method 'getFoo()' cannot be referenced from a static context">getFoo</error>()::foo;
          I i2 = <error descr="'MyTest.Foo.this' cannot be referenced from a static context">this</error>::foo;
          I i3 = Foo :: foo;
       }
    }
}