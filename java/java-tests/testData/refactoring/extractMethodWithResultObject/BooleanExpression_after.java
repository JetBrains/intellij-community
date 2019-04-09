class Test {
    void method(int i) {
        boolean isDirty = newMethod(i).expressionResult || otherTests();
    }

    NewMethodResult newMethod(int i) {
        return new NewMethodResult(i == 0);
    }

    static class NewMethodResult {
        private boolean expressionResult;

        public NewMethodResult(boolean expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    boolean otherTests() { return true; }
}