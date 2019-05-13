import java.util.List;

interface B<T extends Cloneable> {
    void foo(List<? super T> x);
}

class D {
    void bar(B<?> x, List<?> y) {
        x.foo<error descr="'foo(java.util.List<? super capture<?>>)' in 'B' cannot be applied to '(java.util.List<capture<?>>)'">(y)</error>;
    }
}