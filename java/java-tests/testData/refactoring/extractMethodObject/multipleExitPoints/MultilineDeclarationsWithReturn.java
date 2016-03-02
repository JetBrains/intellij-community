class Test {
    public int context1() {
        <selection>int i, j;
        i = 0;
        j = 1;
        if (j > 0) return i;
        </selection>
        return 0;
    }
}