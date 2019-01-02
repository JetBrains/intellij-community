package pck;

class B<K> {}
class A<K> extends B<K> {
    void foo(A<A<String>> b){
        bar<error descr="Ambiguous method call: both 'A.bar(B<? extends A<String>>)' and 'A.bar(A<? extends B<String>>)' match">(b)</error>;
    }

    <T> void bar(B<? extends A<T>> a){}
    <T> void bar(A<? extends B<T>> a){}
}
