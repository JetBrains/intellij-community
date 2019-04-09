class Test {
    void foo() {
        bar(newMethod().expressionResult);
        baz(String.valueOf(1));
    }

    NewMethodResult newMethod() {
        return new NewMethodResult(String.valueOf(1));
    }

    static class NewMethodResult {
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