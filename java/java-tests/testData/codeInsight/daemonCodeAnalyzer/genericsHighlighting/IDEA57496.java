interface I<T>{}
interface A extends I<A[]>{}
interface B extends I<B[]>{}

abstract class c{
    abstract <T> T baz(T x, T y);

    void bar(A x, B y){
        baz(x, y);
    }
}