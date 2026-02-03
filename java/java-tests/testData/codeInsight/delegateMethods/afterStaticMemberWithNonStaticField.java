class List {
    static void foo() {}
}

class Test {
    List l;

    public static void foo() {
        List.foo();
    }
}