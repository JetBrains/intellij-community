class Pos03<U> {

    class Foo<V> {
        Foo(V x) {}
        <Z> Foo(V x, Z z) {}
    }

    void testSimple() {
        Foo<Integer> f1 = new Foo<>(1);
        Foo<? extends Integer> f2 = new Foo<>(1);
        Foo<?> f3 = new Foo<>(1);
        Foo<? super Integer> f4 = new Foo<>(1);

        Foo<Integer> f5 = new Foo<>(1, "");
        Foo<? extends Integer> f6 = new Foo<>(1, "");
        Foo<?> f7 = new Foo<>(1, "");
        Foo<? super Integer> f8 = new Foo<>(1, "");
    }

    void testQualified_1() {
        Foo<Integer> f1 = new Pos03<U>.Foo<>(1);
        Foo<? extends Integer> f2 = new Pos03<U>.Foo<>(1);
        Foo<?> f3 = new Pos03<U>.Foo<>(1);
        Foo<? super Integer> f4 = new Pos03<U>.Foo<>(1);

        Foo<Integer> f5 = new Pos03<U>.Foo<>(1, "");
        Foo<? extends Integer> f6 = new Pos03<U>.Foo<>(1, "");
        Foo<?> f7 = new Pos03<U>.Foo<>(1, "");
        Foo<? super Integer> f8 = new Pos03<U>.Foo<>(1, "");
    }

    void testQualified_2(Pos03<U> p) {
        Foo<Integer> f1 = p.new Foo<>(1);
        Foo<? extends Integer> f2 = p.new Foo<>(1);
        Foo<?> f3 = p.new Foo<>(1);
        Foo<? super Integer> f4 = p.new Foo<>(1);

        Foo<Integer> f5 = p.new Foo<>(1, "");
        Foo<? extends Integer> f6 = p.new Foo<>(1, "");
        Foo<?> f7 = p.new Foo<>(1, "");
        Foo<? super Integer> f8 = p.new Foo<>(1, "");
    }

    public static void main(String[] args) {
        Pos03<String> p3 = new Pos03<>();
        p3.testSimple();
        p3.testQualified_1();
        p3.testQualified_2(p3);
    }
}
