
class A<K>{
    void foo(A<A<?>> b){ <error descr="Inferred type 'A<?>' for type parameter 'T' is not within its bound; should extend 'A<java.lang.Object>'">bar(b)</error>; }
    <S, T extends A<S>> void bar(A<T> a){}
}