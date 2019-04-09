public class EffectivelyLocalWithinExpression {
    void foo() {
        int n;
        if (newMethod().expressionResult) {
            System.out.println();
        }
    }

    NewMethodResult newMethod() {
        int n;
        return new NewMethodResult((n = z()) > 0 && n < 100);
    }

    static class NewMethodResult {
        private boolean expressionResult;

        public NewMethodResult(boolean expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    void bar() {
        int n;
        if ((n = z()) > 1 && n < 100) {
            System.out.println();
        }
    }

    int z() {
        return 1;
    }
}