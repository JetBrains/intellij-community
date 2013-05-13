class Test {
    interface Foo<T> {
        void boo(T t);
    }

    private static void f(Foo<?>... fs) {
         fs[0].boo<error descr="'boo(capture<?>)' in 'Test.Foo' cannot be applied to '(java.lang.String)'">("hey!")</error>;
    }

    private static void f1(Foo<? extends String>... fs) {
         fs[0].boo<error descr="'boo(capture<? extends java.lang.String>)' in 'Test.Foo' cannot be applied to '(java.lang.String)'">("hey!")</error>;
    }
}
