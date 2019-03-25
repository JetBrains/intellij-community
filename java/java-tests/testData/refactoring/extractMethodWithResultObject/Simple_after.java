class C {
  {
    int i = 1;
  }//ins and outs
//exit: SEQUENTIAL PsiClassInitializer

    public NewMethodResult newMethod() {
        int i = 1;
        return new NewMethodResult();
    }

    public class NewMethodResult {
        public NewMethodResult() {
        }
    }
}