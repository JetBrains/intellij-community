interface IA {
    <T extends Cloneable & Iterable> void foo(T x);
    <T extends Iterable & Cloneable> void foo(T x);
}

abstract class A<T extends Throwable> {
    abstract <T extends Comparable<?> & Iterable> void foo(T x, A<?> y);
    abstract <T extends Iterable & Comparable<?>> void foo(T x, A<? extends Throwable> y);
}
