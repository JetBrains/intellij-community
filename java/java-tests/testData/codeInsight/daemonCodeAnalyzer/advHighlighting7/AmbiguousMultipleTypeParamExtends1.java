package pck;

class A<T> {}

interface IA{
    void foo(A<?> x);
}
interface IB{
    <T> void foo(A<T> x);
}
class C {
    <<error descr="'foo(A<T>)' in 'pck.IB' clashes with 'foo(A<?>)' in 'pck.IA'; both methods have same erasure, yet neither overrides the other"></error>T extends IA & IB> void bar(T x, A<String> y){
        x.foo(y);
    }
}