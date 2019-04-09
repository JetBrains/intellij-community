class Foo {
  void foo() {
    int x = 0;
    int y = x = newMethod().expressionResult;
  }

    NewMethodResult newMethod() {
        return new NewMethodResult(1);
    }

    static class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}