class Test {
    void foo(String[] a, int i, boolean b) {
        newMethod(a, i, b);
    }

    private void newMethod(String[] a, int i, boolean b) {
        if (b) {
            System.out.println(a[i]);
        }
    }
}