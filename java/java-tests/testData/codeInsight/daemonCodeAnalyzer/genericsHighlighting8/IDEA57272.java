abstract class A<S> {
    abstract <T extends A<? extends Throwable>> T foo(T y);

    {
        A<?> a = null;
        foo<error descr="'foo(T)' in 'A' cannot be applied to '(A<capture<?>>)'">(a)</error>;
    }
}
