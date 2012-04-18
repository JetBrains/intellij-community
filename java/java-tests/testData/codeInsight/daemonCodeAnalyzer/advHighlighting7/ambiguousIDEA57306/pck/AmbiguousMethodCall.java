package pck;

class A<T> {}

interface IA{
    <T> void foo(A<T> x);
}
interface IB{
    <T extends Exception> void foo(A<T> x);
}
class C {
    <<error descr="'foo(A<T>)' in 'pck.IB' clashes with 'foo(A<T>)' in 'pck.IA'; both methods have same erasure, yet neither overrides the other"></error>T extends IA & IB> void bar(T x, A<Exception> y){
        x.foo(y);
    }
}
