package pck;

interface I<T> {}
interface A extends I<I<? extends String>>{}
interface B extends I<I<?>>{}

abstract class X {
    abstract <T> T foo(T x, T y);

    void bar(A x, B y){
        foo(x, y);
        foo(y, y);
    }
}