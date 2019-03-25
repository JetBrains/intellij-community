class UseBuilder {
    void test(Builder builder, int[] arr) {
        builder.foo("xyz").bar(arr[0]).foo("abc");
    }//ins and outs
//in: PsiParameter:arr
//in: PsiParameter:builder
//exit: EXPRESSION PsiMethodCallExpression:builder.foo("xyz").bar(arr[0])

    public NewMethodResult newMethod(Builder builder, int[] arr) {
        return new NewMethodResult(builder.foo("xyz").bar(arr[0]));
    }

    public class NewMethodResult {
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
