interface Eff<A, B> {
    B f(A a);
}

class Disfunction {
    public static <A, B> Eff<A, B> vary(final Eff<? super A, ? extends B> f) {
        return a -> f.f(a);
    }

    public static <C, A extends C, B, D extends B> Eff<Eff<C, D>, Eff<A, B>> vary() {
        return Disfunction::<A, B>vary;
    }

    public static <C, A extends C, B, D extends B> Eff<Eff<C, D>, Eff<A, B>> vary1() {
        return Disfunction::vary;
    }
}
