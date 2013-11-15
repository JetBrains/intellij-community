class Test {
    interface F<T extends Throwable> {
        void _(T t) throws T;
    }
    <K extends Throwable> void foo(F<K> f) throws K { }

    {
        foo((t)->{});
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo((ClassNotFoundException t)->{});</error>
    }
}