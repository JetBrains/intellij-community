class C {
  {
      NewMethodResult x = newMethod();
      int i = 1;
  }//ins and outs
//exit: SEQUENTIAL PsiClassInitializer

    NewMethodResult newMethod() {
        int i = 1;
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}