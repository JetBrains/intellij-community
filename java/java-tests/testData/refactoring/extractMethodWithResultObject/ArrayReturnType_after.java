class Test {
  String[] foos;

  void test() {
    for (String foo : foos) {

    }
    System.out.println(foos.length);
  }//ins and outs
//exit: EXPRESSION PsiReferenceExpression:foos

    public NewMethodResult newMethod() {
        return new NewMethodResult(foos);
    }

    public class NewMethodResult {
        private String[] expressionResult;

        public NewMethodResult(String[] expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}