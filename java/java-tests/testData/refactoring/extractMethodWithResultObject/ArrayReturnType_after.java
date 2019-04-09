class Test {
  String[] foos;

  void test() {
    for (String foo : newMethod().expressionResult) {

    }
    System.out.println(foos.length);
  }

    NewMethodResult newMethod() {
        return new NewMethodResult(foos);
    }

    static class NewMethodResult {
        private String[] expressionResult;

        public NewMethodResult(String[] expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}