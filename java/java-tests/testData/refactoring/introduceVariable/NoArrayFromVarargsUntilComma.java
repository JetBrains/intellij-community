class Foo {
    void foo(String... strings) {

    }

    void bar() {
        foo(<selection>"a", "b",</selection> new String());
    }
}