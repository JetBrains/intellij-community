class C {
    String[] vars;
    int foo(C c, int i) {
        return newMethod(c, i).expressionResult;
    }

    NewMethodResult newMethod(C c, int i) {
        return new NewMethodResult(c.vars[i].length());
    }

    static class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}