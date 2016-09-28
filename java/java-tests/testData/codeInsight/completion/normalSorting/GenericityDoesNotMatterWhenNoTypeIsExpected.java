class Test {

    void nonGeneric() {}
    <T> T generic() {}

    {
        this.<caret>
    }
}
