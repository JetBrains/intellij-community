class C {
    private static final int VALUE = 2;

    void m(int a, int b) {}

    void m2() {
        m(1, <caret>VALUE);
    }
}