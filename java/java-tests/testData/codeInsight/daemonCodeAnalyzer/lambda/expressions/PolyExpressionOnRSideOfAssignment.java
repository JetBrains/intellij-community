class Demo {
    void foo(String s) {}
    <T> T bar() {
        return null;
    }

    {
        f<caret>oo(unresolvedLefySide = bar());
    }
}
