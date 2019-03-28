class C {
  String[] array;

  boolean test(String a, String b) {
    for (String s : array) {
      if (a.equals(s)) {

        if (b == null) {
          return true;
        }
        if (b.equals(s)) {
          return true;
        }

      }
    }
    return false;
  }//ins and outs
//in: PsiParameter:b
//in: PsiParameter:s
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:true
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:true
//exit: SEQUENTIAL PsiForeachStatement

    public NewMethodResult newMethod(String b, String s) {
        if (b == null) {
            return new NewMethodResult((1 /* exit key */), true);
        }
        if (b.equals(s)) {
            return new NewMethodResult((1 /* exit key */), true);
        }
        return new NewMethodResult((-1 /* exit key */), (false /* missing value */));
    }

    public class NewMethodResult {
        private int exitKey;
        private boolean returnResult;

        public NewMethodResult(int exitKey, boolean returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}