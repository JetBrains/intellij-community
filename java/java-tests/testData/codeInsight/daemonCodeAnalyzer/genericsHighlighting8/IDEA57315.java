class A<K>{
    void foo(A<A<A<String>>> b){ bar(b); }
    <U, S extends A<U>, T extends A<S>> void bar(A<T> a){}
}

class C {
    class B<T> {}
    abstract class A<T extends B<? super B<String>>> {
        void bar(A<? extends B<? super B<String>>> a){
            foo(a);
        }

        <S, T extends B<? super S>> T foo(A<? extends T> a){
            return null;
        }
    }
}