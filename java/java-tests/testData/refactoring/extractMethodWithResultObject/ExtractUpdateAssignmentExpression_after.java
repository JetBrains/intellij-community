class Foo {
  void foo() {
    int x = 0;
    int y = newMethod(x).expressionResult;
  }

    NewMethodResult newMethod(int x) {
        return new NewMethodResult(x += 1);
    }

    static class NewMethodResult {
        private int expressionResult;

        public NewMethodResult(int expressionResult) {
            this.expressionResult = expressionResult;
        }
    }
}