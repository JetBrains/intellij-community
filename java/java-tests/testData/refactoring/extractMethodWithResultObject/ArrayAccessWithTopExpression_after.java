class Test {
  {
    int i = 0;
    double[] doubles = null;

    double progressResult = doubles[0] / i;
  }//ins and outs
//in: PsiLocalVariable:doubles
//in: PsiLocalVariable:i
//exit: SEQUENTIAL PsiClassInitializer

    NewMethodResult newMethod(double[] doubles, int i) {
        double progressResult = doubles[0] / i;
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }
}