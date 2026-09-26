class ThrowCallSiteReturn {
    void test() {
        throw createEx<caret>();
    }

    RuntimeException createEx() {
        return new RuntimeException();
    }
}
