class Foo {
    {
        int a;
        long b;
        int c = a + (int) <caret>b;
    }
}
