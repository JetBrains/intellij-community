import java.io.IOException;

class Test {
    interface F<T extends Throwable> {
        void m() throws T;
    }

    void m1() { }
    void m2() throws NullPointerException{ }
    void m3() throws IOException { }
    <K extends Throwable> void foo(F<K> f) throws K { }

    {
        foo(()->{});
        foo(()->{throw new NullPointerException();});

        foo(this::m1);
        foo(this::m2);
        <error descr="Unhandled exception: java.io.IOException">foo</error>(this::m3);
    }

}