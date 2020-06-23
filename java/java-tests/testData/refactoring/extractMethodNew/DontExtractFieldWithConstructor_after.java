class C {
    final int x1;
    int x2;

    public C(int x1, int x2) {
        this(x1);
        this.x2 = x2;
    }

    private C(int x1) {
        System.out.println();
        this.x1 = x1;
    }

}