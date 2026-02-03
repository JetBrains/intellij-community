class List {
    @SuppressWarnings
    @Deprecated
    void foo() {}
}

class Test {
    List l;

    @Deprecated
    static void foo() {
      l.foo();
    }
    <caret>
}