abstract class A<S> {
    abstract <T> T foo(T x, T y);

    {
        A<? extends A<? extends Throwable>> a = null;
        A<? extends A<?>> b = null;
        foo(a, b);
    }
}
