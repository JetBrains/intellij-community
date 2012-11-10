class A {
    <T> void foo(T[] ts) {}

    {
        foo(new Object[]{<caret>});
    }
}