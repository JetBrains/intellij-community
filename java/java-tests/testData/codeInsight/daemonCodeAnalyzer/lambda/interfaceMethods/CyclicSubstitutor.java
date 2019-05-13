interface Iso<T, R> {
    T deply(R r);

    default Iso<R, T> inverse() {
        final Iso<T, R> z = this;
        return new Iso<R, T>() {
            @Override
            public R deply(T t) {
                throw null;
            }
        };
    }

   static <T, R> Iso<R, T> inverse(Iso<T, R> z) {
        return new Iso<R, T>() {
            @Override
            public R deply(T t) {
                throw null;
            }
        };
    }
}