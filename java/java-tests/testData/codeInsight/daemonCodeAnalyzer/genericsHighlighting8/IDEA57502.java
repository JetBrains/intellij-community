
interface I<T> {}
abstract class X {
    abstract <T> T foo(T x, T y);

    void bar(
      I<I<? super I<? super I<Throwable>>>> x,
      I<I<? super I<? super I<Exception>>>> y){
        I<? extends I<? super I<? super I<?>>>> foo = foo(x, y);
    }
}