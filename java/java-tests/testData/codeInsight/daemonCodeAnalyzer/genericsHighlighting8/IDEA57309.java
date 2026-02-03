class A<S> {
    void bar(A<? super Exception> x, A<? super Throwable> y){
        foo(x, y);
    }

    <T> T foo(A<? super T> x, A<? super T> y){
        return null;
    }
}
