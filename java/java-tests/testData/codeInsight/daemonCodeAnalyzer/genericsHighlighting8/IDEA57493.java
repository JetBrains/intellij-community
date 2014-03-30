abstract class A<T>{
    abstract <S> S foo(S x, S y);
    <S extends Number & Comparable<?>> void baz(A<S> a){}

    void bar(A<Long> x, A<Integer> y){
        baz<error descr="'baz(A<java.lang.Number & java.lang.Comparable<?>>)' in 'A' cannot be applied to '(A<capture<? extends java.lang.Number & java.lang.Comparable<? extends java.lang.Comparable<?>>>>)'">(foo(x, y))</error>;
    }
}
