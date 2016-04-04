class A<T> {
    A<A<? extends T>> foo(){
        return null;
    }

    void bar(A<?> x){
        baz(x.foo());
    }

    <S> void baz(A<A<? extends S>> x){}
}
