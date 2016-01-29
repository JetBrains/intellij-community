class Foo<R> {
    public interface Factory<U> {
        U make();
    }

    interface ASink<R, K extends ASink<R, K>> {
        public void combine(K other);
    }

    static <R, S extends ASink<R, S>> R reduce(Factory<S> factory) {
        return null;
    }

    public void foo() {
        reduce(Moo::new);
        reduce(<error descr="Bad return type in method reference: cannot convert Foo<R>.AMoo to S">AMoo::new</error>);
        reduce(AAMoo::new);
        reduce(AAAMoo::new);
    }

    private class Moo implements ASink<R, Moo> {
        @Override
        public void combine(Moo other) {
        }
    }

    private class AMoo {
    }

    private class AAMoo implements ASink<AAMoo, AAMoo> {
        @Override
        public void combine(AAMoo other) {
        }
    }

    private class AAAMoo implements ASink<R, AAAMoo> {
        private AAAMoo() {
        }

        @Override
        public void combine(AAAMoo other) {
        }
    }
}