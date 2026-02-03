class Foo {
    {
        foo(hash<caret>() -> {});
    }

    void foo(int i, Runnable r) {}
}