class A<T> {}

class B<T> extends A<A<T>> {
    void bar(B<?> b, B<? extends String> eb, B<? super String> sb, B<String> s) {
        foo(b);
        foo(eb);
        foo(sb);
        foo(s);

        foo1<error descr="'foo1(A<A<T>>)' in 'B' cannot be applied to '(B<capture<?>>)'">(b)</error>;
        foo1(eb);
        foo1<error descr="'foo1(A<A<T>>)' in 'B' cannot be applied to '(B<capture<? super java.lang.String>>)'">(sb)</error>;
        foo1(s);

        foo2(b);
        foo2(eb);
        foo2(sb);
        foo2(s);
    
        foo3<error descr="'foo3(A<A<? extends T>>)' in 'B' cannot be applied to '(B<capture<?>>)'">(b)</error>;
        foo3<error descr="'foo3(A<A<? extends T>>)' in 'B' cannot be applied to '(B<capture<? extends java.lang.String>>)'">(eb)</error>;
        foo3<error descr="'foo3(A<A<? extends T>>)' in 'B' cannot be applied to '(B<capture<? super java.lang.String>>)'">(sb)</error>;
        foo3<error descr="'foo3(A<A<? extends T>>)' in 'B' cannot be applied to '(B<java.lang.String>)'">(s)</error>;

        foo4<error descr="'foo4(A<A<? super T>>)' in 'B' cannot be applied to '(B<capture<?>>)'">(b)</error>;
        foo4<error descr="'foo4(A<A<? super T>>)' in 'B' cannot be applied to '(B<capture<? extends java.lang.String>>)'">(eb)</error>;
        foo4<error descr="'foo4(A<A<? super T>>)' in 'B' cannot be applied to '(B<capture<? super java.lang.String>>)'">(sb)</error>;
        foo4<error descr="'foo4(A<A<? super T>>)' in 'B' cannot be applied to '(B<java.lang.String>)'">(s)</error>;

        foo5(b);
        foo5(eb);
        foo5(sb);
        foo5(s);
    }


    <T> void foo(A<A<T>> x) {}
    <T extends String> void foo1(A<A<T>> x) {}
    <T> void foo2(A<? extends A<T>> x) {}
    <T> void foo3(A<A<? extends T>> x) {}
    <T> void foo4(A<A<? super T>> x) {}
    <T> void foo5(A<? super A<T>> x) {}
}
