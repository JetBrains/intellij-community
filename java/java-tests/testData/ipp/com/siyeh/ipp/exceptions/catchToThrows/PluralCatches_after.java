class C {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    abstract void f() throws E1, E2;

    void m() throws E1 {
        try {
            f();
        } catch (E2 ignore) {
        }
    }
}