class C {
    int foo(String[][] vars, int i, int j) {
        return vars[i][j].length();
    }//ins and outs
//in: PsiParameter:i
//in: PsiParameter:j
//in: PsiParameter:vars
//exit: EXPRESSION PsiMethodCallExpression:vars[i][j].length()

    NewMethodResult newMethod(String[][] vars, int i, int j) {
        return new NewMethodResult(vars[i][j].length());
    }

    class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}