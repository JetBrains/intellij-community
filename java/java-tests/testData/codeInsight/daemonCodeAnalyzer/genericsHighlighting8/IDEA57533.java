class C<T extends C<? extends C<? extends T>>>{
    void foo(C<?> x){
        <error descr="Inferred type 'capture<? extends C<? extends C<capture<?>>>>' for type parameter 'T' is not within its bound; should extend 'C<capture<? extends C<? extends C<capture<?>>>>>'">bar(x)</error>;
    }
    <T extends C<? extends T>> void bar(C<T> x){}
}
