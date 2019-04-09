class UseBuilder {
    void test(Builder builder, int[] arr) {
        newMethod(builder, arr).expressionResult.foo("abc");
    }

    NewMethodResult newMethod(Builder builder, int[] arr) {
        return new NewMethodResult(builder.foo("xyz").bar(arr[0]));
    }

    static class NewMethodResult {
        private Builder expressionResult;

        public NewMethodResult(Builder expressionResult) {
            this.expressionResult = expressionResult;
        }
    }

    static class Builder {
        Builder foo(String s) {
            return this;
        }

        Builder bar(int x) {
            return this;
        }
    }
}
