class Test {
    void b<caret>ar(int i) {
        if (i == 0) return;
        bar(i - 1);
    }

    void foo() {
        bar(5);
    }
}