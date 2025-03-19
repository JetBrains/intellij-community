class C {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    static class R implements AutoCloseable {
        public static R open() throws E1 { return new R(); }
        public void close() throws E2 { }
    }

    void m() throws E1 {
        try (R r = R.open()) {
        } catch (E2 ignore) { }
    }
}