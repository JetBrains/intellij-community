class Foo {
  void foo() {
    int x = 0, z = 0;
    int y = x = z = newMethod().expressionResult;
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