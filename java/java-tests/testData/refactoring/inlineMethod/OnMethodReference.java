class Foo {
    interface Jjj {
        int[] jjj(int p);
    }
    void useJjj(Jjj p) {
        p.jjj(9);
    }
    void test() {
        use<caret>Jjj(int[]::new);
    }
}