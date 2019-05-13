class Foo {
    {
        foo(hashCode(), <caret>() -> {});
    }

    void foo(int i, Runnable r) {}
}