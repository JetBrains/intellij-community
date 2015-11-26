
class B<T>{}
class A<T extends B<? extends Runnable>> {
    void bar(A<? extends B<? extends Cloneable>> a){
        foo<error descr="'foo(A<? extends T>)' in 'A' cannot be applied to '(A<capture<? extends B<? extends java.lang.Cloneable>>>)'">(a)</error>;
    }

    <S, T extends B<S>> void foo(A<? extends T> a){}
}