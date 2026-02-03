class A<T> {
    <T extends A<T>> void foo(T x){}

    void bar(A<?> x){
        foo<error descr="'foo(T)' in 'A' cannot be applied to '(A<capture<?>>)'">(x)</error>;
    }
}
