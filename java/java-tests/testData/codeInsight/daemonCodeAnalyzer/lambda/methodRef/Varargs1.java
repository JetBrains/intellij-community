class Test {
    public static void main(String... args) {}
    void test() {
        Foo foo = Test::main;
    }
}

interface Foo {
    void bar();
}

class Test1 {
    public static void main(String... args) {}

    void test() {
        Foo1 foo = Test1::main;
    }
}

interface Foo1 {
    void bar(String... s);
}