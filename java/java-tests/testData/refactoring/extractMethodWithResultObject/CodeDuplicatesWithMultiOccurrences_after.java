class Test {
    void t(java.util.Map<String, String> m) {
        String f = "";
        System.out.println("f = " + f + ", " + m.get(f));
    }//ins and outs
//in: PsiLocalVariable:f
//exit: EXPRESSION PsiReferenceExpression:f

    public NewMethodResult newMethod(String f) {
        return new NewMethodResult(f);
    }

    public class NewMethodResult {
        private String expressionResult;

        public NewMethodResult(String expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}