class C {
  String test(int n) {
    String s = "";

    if (n == 1) {
      return "A" + s;
    }
    if (n == 2) {
      return "B" + s;
    }

    return null;
  }//ins and outs
//in: PsiLocalVariable:s
//in: PsiParameter:n
//exit: RETURN PsiMethod:test<-PsiBinaryExpression:"A" + s
//exit: RETURN PsiMethod:test<-PsiBinaryExpression:"B" + s
//exit: SEQUENTIAL PsiIfStatement

    public NewMethodResult newMethod(int n, String s) {
        if (n == 1) {
            return new NewMethodResult((1 /* exit key */), "A" + s);
        }
        if (n == 2) {
            return new NewMethodResult((1 /* exit key */), "B" + s);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    public class NewMethodResult {
        private int exitKey;
        private String returnResult;

        public NewMethodResult(int exitKey, String returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}