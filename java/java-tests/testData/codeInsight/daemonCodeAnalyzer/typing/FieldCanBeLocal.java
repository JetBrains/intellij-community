class C3 {
    @SuppressWarnings({"UnusedDeclaration"})
    private int xxxx;

    public C3(int xxxx) {
        this.xxxx = xxxx;
    }

    void f(int i) {
        int x = <caret>0;
        f(x+i);
    }
}
