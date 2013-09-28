class Test {
    static class Inner {
        int foo() { return 0; }
        Integer fooBoxed() { return 0; }
    }
    
    void test(Stream<Inner> sp) {
        IntStream mi = sp.map(Inner::foo);
        Stream<Integer> mI = sp.map(Inner::fooBoxed);

        IntStream li = sp.map<error descr="Ambiguous method call: both 'Stream.map(Function<? super Inner,?>)' and 'Stream.map(IntFunction<? super Inner>)' match">(inner->inner.<error descr="Cannot resolve method 'foo()'">foo</error>())</error>;
        Stream<Integer> lI = sp.map<error descr="Ambiguous method call: both 'Stream.map(Function<? super Inner,? extends Integer>)' and 'Stream.map(IntFunction<? super Inner>)' match">(inner -> inner.<error descr="Cannot resolve method 'fooBoxed()'">fooBoxed</error>())</error>;
    }

    interface Stream<T> {
        <R> Stream<R> map(Function<? super T, ? extends R> mapper);
        IntStream map(IntFunction<? super T> mapper);
    }

    interface Function<T, R> {
        public R _(T t);
    }

    interface IntFunction<T> {
        public int _(T t);
    }

    interface IntStream {}
}