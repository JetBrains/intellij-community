class MyTest {
    private static class Foo {
        static int foo(int i) { return i; }
    }

    static Foo foo = new Foo();

    static void foo(String s) {}

    static void bar(Integer i, Number n) {}
    static void bar(Number n, Integer i) {}

    void baz(int i) {}
}

class AlienTest {
    interface IInt {
        int m(int i);
    }

    interface IIntInt {
        int m(Integer i1, Integer i2);
    }

    static {
        IInt i1 = MyTest::<error descr="Cannot resolve method 'abracadabra'">abracadabra</error>;
        IInt i2 = MyTest::<error descr="Cannot resolve method 'foo'">foo</error>;
        IInt i3 = MyTest::<error descr="Cannot resolve method 'bar'">bar</error>;
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IIntInt'">IIntInt i4 = MyTest::bar;</error>
        IInt i5 = <error descr="Non-static method cannot be referenced from a static context">MyTest::baz</error>;
        IInt i6 = <error descr="'foo(int)' is not public in 'MyTest.Foo'. Cannot be accessed from outside package">MyTest.foo::foo</error>;
        IInt i7 = MyTest.<error descr="'MyTest.Foo' has private access in 'MyTest'">Foo</error>::foo;
    }
}

class MyTest1 {
    interface I1 {
        int[] m();
    }

    interface I2 {
        Object m();
    }

    interface I3 {
        char[] m();
    }

    interface I4 {
        boolean  m();
    }

    interface I5 {
        Class<? extends MyTest1> m();
    }

    interface I6 {
        Class<MyTest1> m();
    }

    void foo(Object[] arr) {
        I1 c1 = <error descr="Bad return type in method reference: cannot convert java.lang.Object[] to int[]">arr :: clone</error>;
        I2 c2 = arr :: clone;
        I3 c3 = <error descr="Bad return type in method reference: cannot convert java.lang.Object[] to char[]">arr::clone</error>;
        I4 c5 = <error descr="Bad return type in method reference: cannot convert java.lang.Object[] to boolean">arr::clone</error>;

        I5 c4 = this::getClass;
        I6 c6 = <error descr="Bad return type in method reference: cannot convert java.lang.Class<? extends MyTest1> to java.lang.Class<MyTest1>">this::getClass</error>;
    }
}
