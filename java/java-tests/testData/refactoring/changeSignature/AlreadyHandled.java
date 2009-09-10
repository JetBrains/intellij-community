class Test {
    void foo<caret> () {
    }

    void bar () throws Exception {
        foo();
    }
}
