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
        <error descr="Inferred type 'java.lang.RuntimeException' for type parameter 'K' is not within its bound; should extend 'java.lang.ClassNotFoundException'">foo2(()->{})</error>;
        foo2(()->{ <error descr="Unhandled exception: java.lang.ClassNotFoundException">throw new ClassNotFoundException();</error> });

        <error descr="Inferred type 'java.lang.RuntimeException' for type parameter 'K' is not within its bound; should extend 'java.lang.ClassNotFoundException'">foo2(this::m1)</error>;
        foo2(<error descr="Unhandled exception: java.lang.ClassNotFoundException">this::m2</error>);



        foo1(()->{ <error descr="Unhandled exception: java.lang.ClassNotFoundException">throw new ClassNotFoundException();</error> });
        foo1(()->{ <error descr="Unhandled exception: java.lang.Exception">throw new Exception();</error> });

        foo1(<error descr="Unhandled exception: java.lang.ClassNotFoundException">this::m2</error>);
        foo1(<error descr="Unhandled exception: java.lang.Exception">this::m3</error>);
    }
}
