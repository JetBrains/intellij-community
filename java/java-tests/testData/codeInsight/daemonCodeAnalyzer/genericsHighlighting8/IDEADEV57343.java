class D<T>  {
    void foo(D<?> x){
        bar<error descr="'bar(D<? extends T>, D<? super T>)' in 'D' cannot be applied to '(D<capture<?>>, D<capture<?>>)'">(x,x)</error>;
    }
    <T> void bar(D<? extends T> x, D<? super T> y){}
}
