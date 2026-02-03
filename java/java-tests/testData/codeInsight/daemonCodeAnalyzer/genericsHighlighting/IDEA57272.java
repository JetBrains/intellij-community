abstract class A<S> {
    abstract <T extends A<? extends Throwable>> T foo(T y);

    {
        A<?> a = null;
        <error descr="Inferred type 'A<capture<?>>' for type parameter 'T' is not within its bound; should extend 'A<? extends java.lang.Throwable>'">foo(a)</error>;
    }
}