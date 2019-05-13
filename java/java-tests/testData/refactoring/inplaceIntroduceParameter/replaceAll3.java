class C {
    static enum E {
        A
    }

    void x(String s1, String s2) {}

    private void y() {
        x(E.A.toString(), E.<caret>A.toString());

    }
}
