
class A<K>{
    void foo(A<A<?>> b){ bar<error descr="'bar(A<T>)' in 'A' cannot be applied to '(A<A<?>>)'">(b)</error>; }
    <S, T extends A<S>> void bar(A<T> a){}
}