class UseBuilder {
    void test(Builder builder, int[] arr) {
        builder.foo("xyz").bar(arr[0]).foo("abc");
    }//ins and outs
//in: PsiParameter:arr
//in: PsiParameter:builder
//out: PsiMethodCallExpression:builder.foo("xyz").bar(arr[0])

    static class Builder {
        Builder foo(String s) {
            return this;
        }

        Builder bar(int x) {
            return this;
        }
    }
}
