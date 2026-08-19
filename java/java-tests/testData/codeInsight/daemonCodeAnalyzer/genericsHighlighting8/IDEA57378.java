interface IA {
    <error descr="'foo(T)' is already defined in 'IA'"><T extends Cloneable & Iterable> void foo(T x);</error>
    <error descr="'foo(T)' is already defined in 'IA'"><T extends Iterable & Cloneable> void foo(T x);</error>
}

abstract class A<T extends Throwable> {
    <error descr="'foo(T, A<?>)' is already defined in 'A'">abstract <T extends Comparable<?> & Iterable> void foo(T x, A<?> y);</error>
    <error descr="'foo(T, A<? extends Throwable>)' is already defined in 'A'">abstract <T extends Iterable & Comparable<?>> void foo(T x, A<? extends Throwable> y);</error>
}
