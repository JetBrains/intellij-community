class Test {
    void <caret>foo(String[] s, int a) {}

    {
        foo("a", 1);
    }
}