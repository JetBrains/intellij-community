class A<K>  {
    <S, T extends A<S>> void foo(A<? extends T> x){}

    void bar(A<A<?>> x){
        foo<error descr="'foo(A<? extends T>)' in 'A' cannot be applied to '(A<A<?>>)'">(x)</error>;
    }
}