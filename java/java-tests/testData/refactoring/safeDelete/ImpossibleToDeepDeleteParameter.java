class Test {
    void foo(int i) {
        bar(i);
        baz(i);
    }

    void bar(int <caret>i){}
    void baz(int i){}
}