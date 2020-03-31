class Test {
    void foo() {
        int[] a = new int[10];
        int log = 0;
        while (true) {
            int temp = a.length;
            if (!(1 << log < temp)) break;
            log++;
        }
    }
}