class X {
  private int myI;
  void foo() {
    int i = myI++;
  }//ins and outs
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod() {
        int i = myI++;
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }
}
