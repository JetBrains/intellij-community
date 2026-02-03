
class D<T>  {
    void foo(D<D<?>> x){ this.bar(x); }
    <T extends Throwable> void bar(D<? super D<T>> x){}
}