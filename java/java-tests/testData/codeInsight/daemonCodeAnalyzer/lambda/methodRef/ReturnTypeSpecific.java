class MyTest {
    
    static class Foo<T> {
        T m() { return null; };
    }
    
    interface I1 {
        Foo<Object> m(Foo<String> f);
    }

    interface I2 {
        Integer m(Foo<Integer> f);
    }
    
    interface I3 {
        Object m(Foo<Integer> f);
    }
    
    static void foo(I1 i) {}
    static void foo(I2 i) {}
    static void foo(I3 i) {}

    static {
        foo(Foo::m);
    }
}
