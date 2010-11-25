class Test {
    @Deprecated
    private int f;

    void foo () {
        f = 0;
        int k = f;
    }
}
