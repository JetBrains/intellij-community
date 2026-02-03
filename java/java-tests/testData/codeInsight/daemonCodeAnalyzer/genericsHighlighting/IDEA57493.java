abstract class A<T>{
    abstract <S> S foo(S x, S y);
    <S extends Number & Comparable<?>> void baz(A<S> a){}

    void bar(A<Long> x, A<Integer> y){
        baz(foo(x, y));
    }
}