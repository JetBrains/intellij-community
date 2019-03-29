class Test {
  String[] foos;

  void test() {
    for (String foo : foos) {

    }
    System.out.println(foos.length);
  }//ins and outs
//exit: EXPRESSION PsiReferenceExpression:foos

    NewMethodResult newMethod() {
        return new NewMethodResult(foos);
    }

    class NewMethodResult {
        private String[] expressionResult;

        public NewMethodResult(String[] expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}