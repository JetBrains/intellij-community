class Test {
  {
    int i = 0;
    double[] doubles = null;

    double progressResult = doubles[0] / i;
  }//ins and outs
//in: PsiLocalVariable:doubles
//in: PsiLocalVariable:i
//exit: SEQUENTIAL PsiClassInitializer

    public NewMethodResult newMethod(double[] doubles, int i) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}