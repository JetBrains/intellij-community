class A<T> {
    <T extends A<T>> void foo(T x){}

    void bar(A<?> x){
        <error descr="Inferred type 'A<capture<?>>' for type parameter 'T' is not within its bound; should extend 'A<A<capture<?>>>'">foo(x)</error>;
    }
}