class A {
    private int i;
    private int j;
    private int s;

    public A(int i, int j) {
        this.i = i;
        this.j = j;
    }//ins and outs
//in: PsiParameter:i
//exit: SEQUENTIAL PsiExpressionStatement

    public A(int i, String s) {
        this.s = s;
        this.i = i;
    }
}