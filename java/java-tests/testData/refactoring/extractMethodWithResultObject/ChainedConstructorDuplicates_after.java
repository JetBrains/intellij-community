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

    public NewMethodResult newMethod(int i) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }

    public A(int i, String s) {
        this.i = i;
        this.s = s;
    }
}