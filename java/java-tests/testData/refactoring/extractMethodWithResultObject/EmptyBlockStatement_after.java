class ExtractEmptyBlock {
  void foo() {
      NewMethodResult x = newMethod();
  }

    NewMethodResult newMethod() {
        {}
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}