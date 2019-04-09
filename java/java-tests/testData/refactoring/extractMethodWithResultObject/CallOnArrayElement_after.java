class C {
    int foo(String[] vars, int i) {
        return newMethod(vars, i).expressionResult;
    }

    NewMethodResult newMethod(String[] vars, int i) {
        return new NewMethodResult(vars[i].length());
    }

    static class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}