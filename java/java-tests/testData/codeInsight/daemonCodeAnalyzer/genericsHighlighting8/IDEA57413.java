class A<T> {
    <T extends A<T>> void foo(T x){}

    void bar(A<?> x){
        <error descr="Inferred type 'java.lang.Object' for type parameter 'T' is not within its bound; should extend 'A<java.lang.Object>'">foo(x)</error>;
    }
}
