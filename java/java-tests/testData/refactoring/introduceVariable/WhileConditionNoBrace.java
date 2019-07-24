class Test {
    void foo() {
        int[] a = new int[10];
        int log = 0;
        while (1 << log < <selection>a.length</selection>) log++;
    }
}