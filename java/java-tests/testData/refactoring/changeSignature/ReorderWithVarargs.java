class Test {
    void <caret>foo(String[] s, int a) {}

    {
        foo(new String[]{"a", "bbb"}, 1);
    }
}