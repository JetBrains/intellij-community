class X {
  private int myI;
  void foo() {
    int i = myI++;
  }//ins and outs
//exit: SEQUENTIAL PsiMethod:foo

    NewMethodResult newMethod() {
        int i = myI++;
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
