import java.util.List;

abstract class B {
    abstract <T> T[] foo(List<? super List<T>> x);
    abstract <T> T foo0(List<? super List<T>> x);
    abstract <T> T[] foo1(List<? extends List<T>> x);
    abstract <T> T[] foo2(List<List<? super List<T>>> x);

    void bar(List<List<?>> x, List<List<List<?>>> y){
        foo(x)  [0] = "";
        foo1<error descr="'foo1(java.util.List<? extends java.util.List<T>>)' in 'B' cannot be applied to '(java.util.List<java.util.List<?>>)'">(x)</error> [0] = "";
        foo2<error descr="'foo2(java.util.List<java.util.List<? super java.util.List<T>>>)' in 'B' cannot be applied to '(java.util.List<java.util.List<java.util.List<?>>>)'">(y)</error> [0] = "";

        String s = foo0(x);
    }
}
