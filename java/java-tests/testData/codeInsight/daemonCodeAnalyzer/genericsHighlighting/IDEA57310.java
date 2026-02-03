class A<T> {
    Exception[] bar(A<? super Exception[]> x, A<? super Throwable[]> y){
        return this.foo(x, y);
    }

    <T> T foo(A<? super T> x, A<? super T> y){
        return null;
    }
}