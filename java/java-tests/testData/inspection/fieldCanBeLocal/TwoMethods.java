class Test {
    private int f;

    void foo () {
        f = 0;
        int k = f;
    }

    int bar () {
        f = 5;
        return f;
    }
}
