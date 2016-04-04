import java.util.List;

abstract class B {
    abstract <T> void foo(List<T>[] x);

    void bar(List<?>[] x){
        foo<error descr="'foo(java.util.List<T>[])' in 'B' cannot be applied to '(java.util.List<?>[])'">(x)</error>;
    }
}