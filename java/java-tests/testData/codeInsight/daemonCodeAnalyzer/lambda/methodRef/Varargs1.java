class Test {
    public static void main(String... args) {}
    void test() {
        Foo foo = Test::main;
    }
}

interface Foo {
    void bar();
}