class A {
    private int i;
    private int j;
    private int s;

    public A(int i, int j) {
        this(i);
        this.j = j;
    }

    private A(int i) {
        this.i = i;
    }

    public A(int i, String s) {
        this(i);
        this.s = s;
    }
}