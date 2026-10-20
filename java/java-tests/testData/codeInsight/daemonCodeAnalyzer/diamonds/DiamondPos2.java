class Pos02 {

    static class Foo<X> {
        Foo(X x) {}
        <Z> Foo(X x, Z z) {}
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

    void testQualified() {
        Foo<Integer> f1 = new Pos02.Foo<>(1);
        Foo<? extends Integer> f2 = new Pos02.Foo<>(1);
        Foo<?> f3 = new Pos02.Foo<>(1);
        Foo<? super Integer> f4 = new Pos02.Foo<>(1);

        Foo<Integer> f5 = new Pos02.Foo<>(1, "");
        Foo<? extends Integer> f6 = new Pos02.Foo<>(1, "");
        Foo<?> f7 = new Pos02.Foo<>(1, "");
        Foo<? super Integer> f8 = new Pos02.Foo<>(1, "");
    }

    public static void main(String[] args) {
        Pos02 p2 = new Pos02();
        p2.testSimple();
        p2.testQualified();
    }
}