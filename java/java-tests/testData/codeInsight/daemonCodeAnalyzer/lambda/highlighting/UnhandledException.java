import java.io.IOException;

interface Callable<V> {
    V call() throws Exception;
}

class Test2 {
    static <V> void m(Callable<V> c){}
    {
        m(() -> {throw new IOException();});
    }
}
