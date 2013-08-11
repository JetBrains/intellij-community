class A<T> {
    A<A<? extends T>> foo(){
        return null;
    }

    void bar(A<?> x){
        baz<error descr="'baz(A<A<? extends S>>)' in 'A' cannot be applied to '(A<A<capture<?>>>)'">(x.foo())</error>;
    }

    <S> void baz(A<A<? extends S>> x){}
}