class A<K>  {
    <S, T extends A<S>> void foo(A<? extends T> x){}

    void bar(A<A<?>> x){
        <error descr="Inferred type 'A<?>' for type parameter 'T' is not within its bound; should extend 'A<java.lang.Object>'">foo(x)</error>;
    }
}