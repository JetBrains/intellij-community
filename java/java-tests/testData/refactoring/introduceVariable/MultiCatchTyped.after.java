class C {
    interface B<T> { }
    static class E1 extends Exception implements B<Integer> { }
    static class E2 extends Exception implements B<Long> { }

    void m() {
        try { }
        catch (E1 | E2 ex) {
            final Exception b = ex;
        }
    }
}
