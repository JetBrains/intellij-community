class Test {
    void foo() {
        bar(String.valueOf(1));
        baz(String.valueOf(1));
    }//ins and outs
//exit: EXPRESSION PsiMethodCallExpression:String.valueOf(1)

    private void bar(String s) {
    }

    private void baz(String... s) {
    }
}