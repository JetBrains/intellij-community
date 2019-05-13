class Foo {
    interface I {
        Integer[] _(int p);
    }
    void test() {
        I i = Integer[]:<caret>:new;
    }
}