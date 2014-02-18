class Pos4<U> {

    void test() {
        class Foo<V> {
            Foo(V x) {}
            <Z> Foo(V x, Z z) {}
        }
        Foo<Integer> p1 = new Foo<>(1);
        Foo<? extends Integer> p2 = new Foo<>(1);
        Foo<?> p3 = new Foo<>(1);
        Foo<? super Integer> p4 = new Foo<>(1);

        Foo<Integer> p5 = new Foo<>(1, "");
        Foo<? extends Integer> p6 = new Foo<>(1, "");
        Foo<?> p7 = new Foo<>(1, "");
        Foo<? super Integer> p8 = new Foo<>(1, "");
    }

    public static void main(String[] args) {
        Pos4<String> p4 = new Pos4<>();
        p4.test();
    }
}
