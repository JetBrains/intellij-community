abstract class A<T, S> {
    abstract <T> void foo(A<? extends T, ? extends T> x);
    void  bar(A<? extends Throwable, ? extends Exception> x){
        foo(x);
    }
}

abstract class A0<T, S> {
    abstract <T> void foo(A0<? extends T, ? extends T> x);
    void  bar(A0<? extends Exception, ? extends Throwable> x){
        foo(x);
    }
}

abstract class A1<T, S> {
    abstract <T> void foo(A1<? extends T, ? extends T> x);
    void  bar(A1<? extends Throwable, Exception> x){
        foo(x);
    }
}

abstract class A10<T, S> {
    abstract <T> void foo(A10<? extends T, ? extends T> x);
    void  bar(A10<Throwable, Exception> x){
        foo(x);
    }
}

abstract class A2<T, S> {
    abstract <T> void foo(A2<? super T, ? super T> x);
    void  bar(A2<? super Exception, ? super Throwable> x){
        foo(x);
    }
}

abstract class A20<T, S> {
    abstract <T> void foo(A20<? super T, ? super T> x);
    void  bar(A20<? super Throwable, ? super Exception> x){
        foo(x);
    }
}