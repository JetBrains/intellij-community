class Super {
    void foo(int <caret>i) {}
    int bar() {return 0;}

    {
        foo(bar());
    }
}
