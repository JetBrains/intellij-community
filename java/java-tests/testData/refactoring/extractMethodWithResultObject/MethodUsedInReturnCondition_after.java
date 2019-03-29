class C {
  String test() {
    while (true) {
      try {
        return get();
      }
      catch (Exception e) {}

        NewMethodResult x = newMethod();
        if (foo()) {
        return null;
      }
      if (bar()) {
        return null;
      }

    }
  }//ins and outs
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:null
//exit: RETURN PsiMethod:test<-PsiLiteralExpression:null
//exit: SEQUENTIAL PsiBlockStatement

    NewMethodResult newMethod() {
        if (foo()) {
            return new NewMethodResult((1 /* exit key */), null);
        }
        if (bar()) {
            return new NewMethodResult((1 /* exit key */), null);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    class NewMethodResult {
        private int exitKey;
        private String returnResult;

        public NewMethodResult(int exitKey, String returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    String get() { return null; }
  boolean foo() { return false; }
  boolean bar() { return false; }
}