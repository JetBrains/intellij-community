class Test {
    static <T> T f<caret>oo() {
        return null;
    }

    void bar() {
       foo();
    }
}