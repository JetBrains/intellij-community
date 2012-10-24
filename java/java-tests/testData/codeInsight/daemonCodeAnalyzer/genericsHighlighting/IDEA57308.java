class A<T> {}

interface IA{
    <T> void foo(A<? extends T[]> x);
}
interface IB{
    <T> int foo(A<? extends T> x);
}
class C {
    <T extends IA & IB> void bar(T x, A<String[]> y){
        <error descr="Incompatible types. Found: 'void', required: 'int'">int z = x.foo(y);</error>
    }
}