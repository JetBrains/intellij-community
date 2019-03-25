class C {
    String[] vars;
    int foo(C c, int i) {
        return c.vars[i].length();
    }//ins and outs
//in: PsiParameter:c
//in: PsiParameter:i
//exit: EXPRESSION PsiMethodCallExpression:c.vars[i].length()

    public NewMethodResult newMethod(C c, int i) {
        return new NewMethodResult(c.vars[i].length());
    }

    public class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}