class Test {

    interface I {
        <T> String m();
    }

    static String foo() { return null; }

    public I get() {
        return Test::foo;
    }
}
