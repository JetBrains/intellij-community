class Test {
    void t(java.util.Map<String, String> m) {
        String f = "";
        System.out.println("f = " + newMethod(f).expressionResult + ", " + m.get(f));
    }

    NewMethodResult newMethod(String f) {
        return new NewMethodResult(f);
    }

    static class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}