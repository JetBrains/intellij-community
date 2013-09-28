class C {
    static enum E {
        A
    }

    void x(String s1, String s2) {}

    private void y(E a) {
        x(a.toString(), a.toString());

    }
}
