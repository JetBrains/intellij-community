package pck;

class B<K> {}
class A<K> extends B<K> {
    void foo(A<A<String>> b){
        bar(b);
    }

    <T> void bar(B<? extends A<?>> a){}
    <T> void bar(A<? extends A<T>> a){}
}