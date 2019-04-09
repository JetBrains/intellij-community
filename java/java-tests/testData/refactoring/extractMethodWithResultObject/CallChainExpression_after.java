class CallChainExpression {
    String foo() {
        String s = newMethod().expressionResult;
        System.out.println(s);
        return s;
    }

    NewMethodResult newMethod() {
        return new NewMethodResult(A.a().b().c);
    }

    static class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    static class A {
        static A a() { return new A(); }
        A b() { return this; }
        String c = "";
    }
}