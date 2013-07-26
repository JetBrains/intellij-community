class C<T> {
    void foo(C<C<?>> x) {
        C<Object> c = bar(x);
    }
    <T> C<T> bar(C<? super C<T>> x){
        return null;
    }
}