
import java.util.List;

class OuterClass<E> {
    private class InnerClass {
        void foo(final E s) {
            System.out.println(s);
        }
    }

    void f(List<OuterClass<? extends String>.InnerClass> l) {
        l.get(0).foo<error descr="'foo(capture<? extends java.lang.String>)' in 'OuterClass.InnerClass' cannot be applied to '(java.lang.String)'">("")</error>;
        baz(bar(l));
    }

    private <M> void baz(final OuterClass<? extends M>.InnerClass bar) { }

    <T> OuterClass<? extends T>.InnerClass bar(List<OuterClass<? extends T>.InnerClass> l) {
        return l.get(0);
    }
}