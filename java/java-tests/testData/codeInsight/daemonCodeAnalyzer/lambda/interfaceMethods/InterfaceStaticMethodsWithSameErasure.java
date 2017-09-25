import java.util.List;

class InterfaceStaticMethodsWithSameErasure {
    interface A<I, R> {
        static <I, R> A<I, R> foo(List<I> a) { return null; }
    }

    interface B<I, R> extends A<I, R> {
        static <I, R> B<I, R> foo(List<R> b) { return null; }
    }

    static abstract class C<I, R> implements B<I, R> { }

    static class D<I, R> extends C<I, R> { }
}