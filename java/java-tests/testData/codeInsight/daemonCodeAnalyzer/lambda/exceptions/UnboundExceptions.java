class Test {

    interface F<T extends Throwable> {
        void m() throws T;
    }

    void m1() { }
    void m2() throws ClassNotFoundException { }
    void m3() throws Exception { }

    <K extends Throwable> void foo1(F<K> f) throws K { }
    <K extends ClassNotFoundException> void foo2(F<K> f) throws K { }

    {
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2</error>(()->{});
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2</error>(()->{ throw new ClassNotFoundException(); });

        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2</error>(this::m1);
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2</error>(this::m2);



        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo1</error>(()->{ throw new ClassNotFoundException(); });
        <error descr="Unhandled exception: java.lang.Exception">foo1</error>(()->{ throw new Exception(); });

        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo1</error>(this::m2);
        <error descr="Unhandled exception: java.lang.Exception">foo1</error>(this::m3);
    }
}
