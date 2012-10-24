class A<K>{
    void foo(A<A<A<String>>> b){ <error descr="Inferred type 'A<java.lang.String>' for type parameter 'S' is not within its bound; should extend 'A<java.lang.Object>'">bar(b)</error>; }
    <U, S extends A<U>, T extends A<S>> void bar(A<T> a){}
}