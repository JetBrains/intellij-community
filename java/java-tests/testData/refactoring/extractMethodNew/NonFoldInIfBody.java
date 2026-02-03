class Test {
    void foo(String[] a, int i, boolean b) {
        <selection>if (b) {
            System.out.println(a[i]);
        }</selection>
    }
}