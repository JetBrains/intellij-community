package pck;

class A<T> {}

interface IA{
    <T> void foo(A<A<T>> x);
}
interface IB{
    <T> void foo(A<? super A<T>> x);
}
class C {
    <<error descr="'foo(A<A<T>>)' in 'pck.IA' clashes with 'foo(A<? super A<T>>)' in 'pck.IB'; both methods have same erasure, yet neither overrides the other"></error>T extends IB & IA> void bar(T x, A<A<String>> y){
        x.foo(y);
    }
}