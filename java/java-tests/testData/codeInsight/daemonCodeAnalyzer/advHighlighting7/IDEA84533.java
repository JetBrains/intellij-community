public class Main {
    static class A {}
    static class B extends A {}
    static class X<T> { }
    static class Y<T> extends X<T> { }

    private void test1(X<B> x) {
        Y<A> y = <error descr="Inconvertible types; cannot cast 'Main.X<Main.B>' to 'Main.Y<Main.A>'">(Y<A>) x</error>;
    }

    private void test2(X<A> x) {
        Y<A> y = (Y<A>) x;
    }

    private void test3(Y<B> y1) {
        @SuppressWarnings("unchecked")
        Y<A> y2 = <error descr="Inconvertible types; cannot cast 'Main.Y<Main.B>' to 'Main.Y<Main.A>'">(Y<A>) y1</error>;
    }
}
