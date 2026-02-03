class Foreign {
}

class Test {
    int field;

    void <caret>foo (Foreign f) {
        field++;
    }

    void bar () {
        foo(new Foreign());
    }
}
