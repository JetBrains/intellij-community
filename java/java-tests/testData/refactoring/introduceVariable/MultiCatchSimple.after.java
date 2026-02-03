class C {
    static class E1 extends Exception { }
    static class E2 extends Exception { }

    void m() {
        try { }
        catch (E1 | E2 ex) {
            final Exception e = ex;
        }
    }
}
