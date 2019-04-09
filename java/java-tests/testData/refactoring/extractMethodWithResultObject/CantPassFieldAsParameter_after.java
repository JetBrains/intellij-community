class X {
  private int myI;
  void foo() {
      NewMethodResult x = newMethod();
  }

    NewMethodResult newMethod() {
        int i = myI++;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
