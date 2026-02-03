class Test {
    void test(int[] array, int start, int end) {
        int[] test = new int[10];
        // first
        // second
        // third
        // fourth
        // fifth
        // sixth
        // seventh
        if (array.length - (end - start) >= 0)
            System.arraycopy(array, end - start, test, end - start - (end - start), array.length - (end - start));
    }
}