class Test {

    interface F<T extends Throwable> {
        void _() throws T;
    }
    
    void m1() { }
    void m2() throws NullPointerException{ }
    <K extends Throwable> void foo(F<K> f) throws K { }
    
    {
        foo(()->{});
        foo(()->{throw new NullPointerException();});

        foo(this::m1);
        foo(this::m2);
    }
    
}