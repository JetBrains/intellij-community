package pck;

class A<T> {}

interface IA{
    void foo(A<?> x);
}
interface IB{
    <T> void foo(A<T> x);
}
class C {
    <T extends IA & IB> void bar(T x, A<String> y){
        x.foo<error descr="Ambiguous method call: both 'IA.foo(A<?>)' and 'IB.foo(A<String>)' match">(y)</error>;
    }
}