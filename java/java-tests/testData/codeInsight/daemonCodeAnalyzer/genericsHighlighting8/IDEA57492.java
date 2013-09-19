abstract class A<T>{
    abstract <S> S foo(S x, S y);
    <S extends Number & Comparable<? extends Number>> void baz(A<S> a){}

    void bar(A<Long> x, A<Integer> y){
        baz<error descr="'baz(A<S>)' in 'A' cannot be applied to '(A<capture<? extends java.lang.Number & java.lang.Comparable<? extends java.lang.Comparable<?>>>>)'">(foo(x, y))</error>;
    }
}

abstract class A1<T>{
    abstract <S> S foo(S x, S y);
    <T extends Number & Comparable<?>, S extends Number & Comparable<? extends T>> void baz(A1<S> a){}

    void bar(A1<Long> x, A1<Integer> y){
        baz<error descr="'baz(A1<S>)' in 'A1' cannot be applied to '(A1<capture<? extends java.lang.Number & java.lang.Comparable<? extends java.lang.Comparable<?>>>>)'">(foo(x, y))</error>;
    }
}
