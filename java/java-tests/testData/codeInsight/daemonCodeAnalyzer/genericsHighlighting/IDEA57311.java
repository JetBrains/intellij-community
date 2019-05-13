class A<T> {
    A<A<? extends T>> foo(){
        return null;
    }

    void bar(A<?> x){
        baz<error descr="'baz(A<A<?>>)' in 'A' cannot be applied to '(A<A<? extends capture<?>>>)'">(x.foo())</error>;
    }

    <S> void baz(A<A<? extends S>> x){}
}