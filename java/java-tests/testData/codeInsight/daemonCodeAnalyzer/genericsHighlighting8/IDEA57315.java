class A<K>{
    void foo(A<A<A<String>>> b){ bar(b); }
    <U, S extends A<U>, T extends A<S>> void bar(A<T> a){}
}
