class A {
    private int i;
    private int j;

    public A(int i, int j) {
        NewMethodResult x = newMethod(i);
        this.j = j;
    }//ins and outs
//in: PsiParameter:i
//exit: SEQUENTIAL PsiExpressionStatement

    NewMethodResult newMethod(int i) {
        this.i = i;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}