class A<T> {}

interface IA{
    <T> void foo(A<? extends T[]> x);
}
interface IB{
    <T> int foo(A<? extends T> x);
}
class C {
    <<error descr="'foo(A<? extends T>)' in 'IB' clashes with 'foo(A<? extends T[]>)' in 'IA'; both methods have same erasure, yet neither overrides the other"></error><error descr="'foo(A<? extends T>)' in 'IB' clashes with 'foo(A<? extends T[]>)' in 'IA'; both methods have same erasure, yet neither overrides the other"></error>T extends IA & IB> void bar(T x, A<String[]> y){
        <error descr="Incompatible types. Found: 'void', required: 'int'">int z = x.foo(y);</error>
    }
}
