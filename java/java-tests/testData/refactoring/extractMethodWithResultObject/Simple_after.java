class C {
  {
      NewMethodResult x = newMethod();
  }//ins and outs
//exit: SEQUENTIAL PsiClassInitializer

    NewMethodResult newMethod() {
        int i = 1;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}