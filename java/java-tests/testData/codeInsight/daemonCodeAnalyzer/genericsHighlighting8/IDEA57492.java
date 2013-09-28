abstract class A<T>{
    abstract <S> S foo(S x, S y);
    <S extends Number & Comparable<? extends Number>> void baz(A<S> a){}

    void bar(A<Long> x, A<Integer> y){
        <error descr="Inferred type 'capture<? extends java.lang.Number & java.lang.Comparable<? extends java.lang.Comparable<?>>>' for type parameter 'S' is not within its bound; should implement 'java.lang.Comparable<? extends java.lang.Number>'">baz(foo(x, y))</error>;
    }
}

abstract class A1<T>{
    abstract <S> S foo(S x, S y);
    <T extends Number & Comparable<?>, S extends Number & Comparable<? extends T>> void baz(A1<S> a){}

    void bar(A1<Long> x, A1<Integer> y){
        <error descr="Inferred type 'capture<? extends java.lang.Number & java.lang.Comparable<? extends java.lang.Comparable<?>>>' for type parameter 'S' is not within its bound; should implement 'java.lang.Comparable<? extends java.lang.Number & java.lang.Comparable<?>>'">baz(foo(x, y))</error>;
    }
}
