class A<T, S> {
}

class B<L> {
    A<L, L> foo() {
        return null;
    }

    void bar(B<?> b, A<?, ?> foo1) {
        baz(b.foo());
        A<?, ?> foo = b.foo();
        baz<error descr="'baz(A<K,K>)' in 'B' cannot be applied to '(A<capture<?>,capture<?>>)'">(foo)</error>;
        baz<error descr="'baz(A<K,K>)' in 'B' cannot be applied to '(A<capture<?>,capture<?>>)'">(foo1)</error>;
    }

    <K> void baz(A<K, K> a) {
    }
}



class C<T,S>{}
class D<T> extends C<T,T> {
    void foo(D<?> x){ bar(x); }
    <T> void bar(C<T,T> x){}
}
