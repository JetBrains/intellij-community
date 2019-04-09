class C {
  {
      NewMethodResult x = newMethod();
  }

    NewMethodResult newMethod() {
        int i = 1;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}