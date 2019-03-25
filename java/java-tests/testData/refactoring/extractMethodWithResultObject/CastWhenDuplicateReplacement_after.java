class Test {

    void foo(Object x) {
        if (x instanceof String) x = ((String)x).substring(1);
        if (x instanceof String) x = ((String)x).substring(1);
    }//ins and outs
//in: PsiParameter:x
//exit: EXPRESSION PsiMethodCallExpression:((String)x).substring(1)

    public NewMethodResult newMethod(Object x) {
        return new NewMethodResult(((String)x).substring(1));
    }

    public class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}