class Test {
    void foo() {
        bar(newMethod().expressionResult);
        baz(String.valueOf(1));
    }//ins and outs
//exit: EXPRESSION PsiMethodCallExpression:String.valueOf(1)

    NewMethodResult newMethod() {
        return new NewMethodResult(String.valueOf(1));
    }

    class NewMethodResult {
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