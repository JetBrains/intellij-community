class Test {
    interface F<T extends ClassNotFoundException> {
        void _() throws T;
    }
    <K extends ClassNotFoundException> void foo(F<K> f) throws K { }

    {
        <error descr="Unhandled exception: K">foo(() -> {});</error>
    }
}