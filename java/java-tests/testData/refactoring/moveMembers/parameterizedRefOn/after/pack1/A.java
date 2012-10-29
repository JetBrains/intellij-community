package pack1;

interface Eff<A, B> {
    B f(A a);
}

abstract class POne<A> {
    abstract A _1();

}

final class Hooray<A> {
    public <B> Hooray<B> map(final Eff<A, B> f) {
        return null;
    }
}
