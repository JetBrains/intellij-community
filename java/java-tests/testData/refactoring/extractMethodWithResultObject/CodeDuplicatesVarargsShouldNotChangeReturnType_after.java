class Test {
    void foo() {
        bar(String.valueOf(1));
        baz(String.valueOf(1));
    }//ins and outs
//exit: EXPRESSION PsiMethodCallExpression:String.valueOf(1)

    public NewMethodResult newMethod() {
        return new NewMethodResult(String.valueOf(1));
    }

    public class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    private void bar(String s) {
    }

    private void baz(String... s) {
    }
}