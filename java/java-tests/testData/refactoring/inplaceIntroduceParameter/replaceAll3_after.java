class C {
    static enum E {
        A
    }

    void x(String s1, String s2) {}

    private void y(E e) {
        x(e.toString(), e.toString());

    }
}
