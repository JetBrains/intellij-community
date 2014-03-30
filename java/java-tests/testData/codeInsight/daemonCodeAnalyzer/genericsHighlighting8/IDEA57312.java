class A<T> {
    A<A<? super A<T>>> foo(){
        return null;
    }

    void bar(A<?> x){
        baz(x.foo());
    }

    <S> void baz(A<A<? super A<S>>> x){}
}
