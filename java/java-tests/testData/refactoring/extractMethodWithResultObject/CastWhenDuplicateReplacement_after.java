class Test {

    void foo(Object x) {
        if (x instanceof String) x = ((String)x).substring(1);
        if (x instanceof String) x = newMethod(x).expressionResult;
    }

    NewMethodResult newMethod(Object x) {
        return new NewMethodResult(((String)x).substring(1));
    }

    static class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}