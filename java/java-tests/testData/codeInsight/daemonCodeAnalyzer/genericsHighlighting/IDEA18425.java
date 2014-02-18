abstract class BiFunction<A,B> {
    public abstract B apply(A a);
    public abstract A unapply(B b);
    public BiFunction<B,A> flip() {
        return new BiFunction<B, A>() {
            public A apply(B b) {
                return BiFunction.this.unapply(b);
            }

            public B unapply(A a) {
                return BiFunction.this.apply(a);
            }
        };
    }
}