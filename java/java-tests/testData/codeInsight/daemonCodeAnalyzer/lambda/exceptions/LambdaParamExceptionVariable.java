class Test {
    interface F<T extends Throwable> {
        void m(T t) throws T;
    }
    <K extends Throwable> void foo(F<K> f) throws K { }

    {
        foo((t)->{});
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo</error>((ClassNotFoundException t)->{});
    }
}