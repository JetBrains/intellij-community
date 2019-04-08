class X {
  private int myI;
  void foo() {
      NewMethodResult x = newMethod();
  }//ins and outs
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod() {
        int i = myI++;
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
