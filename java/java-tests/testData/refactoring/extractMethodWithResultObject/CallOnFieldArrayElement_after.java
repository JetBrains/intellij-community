class C {
    String[] vars;
    int foo(C c, int i) {
        return newMethod(c, i).expressionResult;
    }//ins and outs
//in: PsiParameter:c
//in: PsiParameter:i
//exit: EXPRESSION PsiMethodCallExpression:c.vars[i].length()

    NewMethodResult newMethod(C c, int i) {
        return new NewMethodResult(c.vars[i].length());
    }

    class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}