class A<T,S> {}
class B<T>  {
    A<? extends T, ? extends T> foo(){
        return null;
    }

    void bar(B<?> b){ baz<error descr="'baz(A<? extends T,T>)' in 'B' cannot be applied to '(A<capture<? extends capture<?>>,capture<? extends capture<?>>>)'">(b.foo())</error>; }
    <T> void baz(A<? extends T,T> a) {}
}