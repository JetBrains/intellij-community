class Test {
  String foo(String[] args) {

    for(String arg : args) {
      if (arg == null) continue;
      System.out.println(arg);
    }
    if (args.length == 0) return null;

    return null;
  }//ins and outs
//in: PsiParameter:args
//exit: RETURN PsiMethod:foo<-PsiLiteralExpression:null
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(String[] args) {
        for(String arg : args) {
          if (arg == null) continue;
          System.out.println(arg);
        }
        if (args.length == 0) return new NewMethodResult((1 /* exit key */), null);
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
}
