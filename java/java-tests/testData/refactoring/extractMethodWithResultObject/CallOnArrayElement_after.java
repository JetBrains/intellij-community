class C {
    int foo(String[] vars, int i) {
        return vars[i].length();
    }//ins and outs
//in: PsiParameter:i
//in: PsiParameter:vars
//exit: EXPRESSION PsiMethodCallExpression:vars[i].length()

    public NewMethodResult newMethod(String[] vars, int i) {
        return new NewMethodResult(vars[i].length());
    }

    public class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}