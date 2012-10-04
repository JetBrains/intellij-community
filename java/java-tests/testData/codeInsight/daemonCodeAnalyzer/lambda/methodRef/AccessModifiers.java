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
        IInt i6 = <error descr="'foo(int)' is not public in 'MyTest.Foo'. Cannot be accessed from outside package">MyTest.foo::foo</error>;
        IInt i7 = MyTest.<error descr="'MyTest.Foo' has private access in 'MyTest'">Foo</error>::foo;
    }
}
