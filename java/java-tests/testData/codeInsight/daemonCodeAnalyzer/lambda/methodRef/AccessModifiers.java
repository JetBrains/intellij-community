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
        int _(int i);
    }
    
    interface IIntInt {
        int _(Integer i1, Integer i2);
    }

    static {
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IInt'">IInt i1 = MyTest::abracadabra;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IInt'">IInt i2 = MyTest::foo;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IInt'">IInt i3 = MyTest::bar;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IIntInt'">IIntInt i4 = MyTest::bar;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IInt'">IInt i5 = MyTest::baz;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'AlienTest.IInt'">IInt i6 = MyTest.foo::foo;</error>
        IInt i7 = MyTest.<error descr="'MyTest.Foo' has private access in 'MyTest'">Foo</error>::foo;
    }
}


class MyTest1 {
    
    interface I1 {
        int[] _();
    }
    
    interface I2 {
        Object _();
    }
    
    interface I3 {
        char[] _();
    }
    
    interface I4 {

        boolean  _();
    }

    interface I5 {
        Class<? extends MyTest1> _();
    }

    interface I6 {
        Class<MyTest1> _();
    }
    
    void foo(Object[] arr) {
        <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest1.I1'">I1 c1 = arr :: clone;</error>
        I2 c2 = arr :: clone;
        <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest1.I3'">I3 c3 = arr::clone;</error>
        <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest1.I4'">I4 c5 = arr::clone;</error>

        I5 c4 = this::getClass;
        <error descr="Incompatible types. Found: '<method reference>', required: 'MyTest1.I6'">I6 c6 = this::getClass;</error>
    }
}
