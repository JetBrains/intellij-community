class A<T> {
    <T extends A<T>> void foo(T x){}

    void bar(A<?> x){
        <error descr="Inferred type 'capture<?>' for type parameter 'T' is not within its bound; should extend 'A<capture<? extends A<capture<?>>>>'">foo(x)</error>;
    }
}
