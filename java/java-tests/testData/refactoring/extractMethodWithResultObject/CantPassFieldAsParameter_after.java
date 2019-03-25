class X {
  private int myI;
  void foo() {
    int i = myI++;
  }//ins and outs
//exit: SEQUENTIAL PsiMethod:foo

    public NewMethodResult newMethod() {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}
