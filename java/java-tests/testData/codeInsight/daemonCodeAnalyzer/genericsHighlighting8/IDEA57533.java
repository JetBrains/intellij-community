class C<T extends C<? extends C<? extends T>>>{
    void foo(C<?> x){
        bar<error descr="'bar(C<T>)' in 'C' cannot be applied to '(C<capture<?>>)'">(x)</error>;
    }
    <T extends C<? extends T>> void bar(C<T> x){}
}
