class Test {
    void foo() {
        bar(String.valueOf(1));
        baz(String.valueOf(1));
    }//ins and outs
//exit: EXPRESSION PsiMethodCallExpression:String.valueOf(1)

    public NewMethodResult newMethod() {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }

    private void bar(String s) {
    }

    private void baz(String... s) {
    }
}