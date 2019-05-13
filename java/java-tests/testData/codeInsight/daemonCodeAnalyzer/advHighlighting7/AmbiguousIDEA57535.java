package pck;

abstract class C{
    abstract <T extends Comparable<?>> void foo(T x);
    abstract <T extends Number & Comparable<?>> void foo(T x);
    void bar(Integer x){
        foo(x);
    }
}
