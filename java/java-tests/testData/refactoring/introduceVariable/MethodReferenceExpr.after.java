class Foo {
    interface I {
        Integer[] _(int p);
    }
    void test() {
        I m = Integer[]::new;
        I i = m;
    }
}