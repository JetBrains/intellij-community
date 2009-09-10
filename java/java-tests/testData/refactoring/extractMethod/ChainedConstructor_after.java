class A {
    private int i;
    private int j;

    public A(int i, int j) {
        this(i);
        this.j = j;
    }

    private A(int i) {
        this.i = i;
    }
}