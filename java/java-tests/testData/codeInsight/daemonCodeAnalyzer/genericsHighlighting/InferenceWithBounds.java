import java.util.*;

class CLS {
    static <V extends String>  void bar (V v) {}

    static void foo () {
        bar<error descr="'bar(java.lang.String)' in 'CLS' cannot be applied to '(java.lang.Object)'">(new Object())</error>;
    }
}
//////////////////////////////
public abstract class ZZZ<K> {
  public abstract <T extends String> ZZZ<T> get();
}
class Z2<K> extends ZZZ<K> {
    public <T extends String> Z2<T> get() {
        return null;
    }
    void f() {
        Z2 z2 = get();
    }
}
/////////////////
abstract class LeastRecentlyUsedCache {
interface Callable<V> {
    V call() throws Exception;
}

    <E extends A> Callable<E> e(E e) {
        return null;
    }
    <T extends B> Callable<T> f(boolean b, final T t) {
        return b ? e(t) : new Callable<T>() {
            public T call() throws Exception {
                return t;
            }
        };
    }

    void ff() {

    }

    class A {}
    class B extends A {}
}
//////////////////////////
public class BadCodeGreen<T, C extends Collection<? extends T>> {
    public BadCodeGreen(C c, T t) {
        c.add<error descr="'add(capture<? extends T>)' in 'java.util.Collection' cannot be applied to '(T)'">(t)</error>;
    }
}
////////////////////////////
abstract class A {
    public abstract <T extends List<?>> T create();
}
class B extends A {
    public <T extends List<?>> T create() {
        return null;
    }
}
///////////////////////////