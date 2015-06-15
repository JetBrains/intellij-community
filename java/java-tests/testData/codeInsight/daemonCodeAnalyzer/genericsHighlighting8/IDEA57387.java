
class B<T>{}
class A<T extends B<? extends Runnable>> {
    void bar(A<? extends B<? extends Cloneable>> a){
        <error descr="Inferred type 'B<? extends java.lang.Cloneable>' for type parameter 'T' is not within its bound; should extend 'B<java.lang.Object>'">foo(a)</error>;
    }

    <S, T extends B<S>> void foo(A<? extends T> a){}
}