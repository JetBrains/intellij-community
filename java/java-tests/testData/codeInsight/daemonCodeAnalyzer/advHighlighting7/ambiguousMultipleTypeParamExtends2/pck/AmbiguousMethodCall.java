package pck;

class A<T> {}

interface IA{
    <T> void foo(A<A<T>> x);
}
interface IB{
    <T> void foo(A<? super A<T>> x);
}
class C {
    <T extends IB & IA> void bar(T x, A<A<String>> y){
        x.foo(y);
    }
}