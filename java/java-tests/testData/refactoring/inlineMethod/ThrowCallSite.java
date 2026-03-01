class ThrowCallSite {
    void test() {
        throw /*test*/ throwEx<caret>();
    }

    RuntimeException throwEx() {
        throw new RuntimeException();
    }
}
