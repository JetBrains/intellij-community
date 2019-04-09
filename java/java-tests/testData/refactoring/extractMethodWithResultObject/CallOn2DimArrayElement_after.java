class C {
    int foo(String[][] vars, int i, int j) {
        return newMethod(vars, i, j).expressionResult;
    }

    NewMethodResult newMethod(String[][] vars, int i, int j) {
        return new NewMethodResult(vars[i][j].length());
    }

    static class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}