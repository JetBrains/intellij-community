abstract class A<T> {
    void foo(T x){}
    void bar(A<? super Throwable> a, Object b){
        a.foo((<caret>) b);
    }
}