class Test {
    interface F<T extends ClassNotFoundException> {
        void m() throws T;
    }
    <K extends ClassNotFoundException> void foo(F<K> f) throws K { }

    {
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo</error>(() -> {});
    }
}