class Test {

    interface F<T extends Throwable> {
        void _() throws T;
    }

    void m1() { }
    void m2() throws ClassNotFoundException { }
    void m3() throws Exception { }
    
    <K extends Throwable> void foo1(F<K> f) throws K { }
    <K extends ClassNotFoundException> void foo2(F<K> f) throws K { }

    {
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2(()->{});</error>
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2(()->{ throw new ClassNotFoundException(); });</error>

        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2(this::m1);</error>
        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo2(this::m2);</error>



        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo1(()->{ throw new ClassNotFoundException(); });</error>
        <error descr="Unhandled exception: java.lang.Exception">foo1(()->{ throw new Exception(); });</error>

        <error descr="Unhandled exception: java.lang.ClassNotFoundException">foo1(this::m2);</error>
        <error descr="Unhandled exception: java.lang.Exception">foo1(this::m3);</error>
    }
}
