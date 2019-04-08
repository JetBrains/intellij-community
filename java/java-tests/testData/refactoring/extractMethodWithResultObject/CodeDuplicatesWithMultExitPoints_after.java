class A {
   int foo (Object o) {
       NewMethodResult x = newMethod(o);
       if (x.exitKey == 1) return x.returnResult;
       if (o == null) return 0;
     if (o == null) return 0;
     return 1;
   }//ins and outs
//in: PsiParameter:o
//exit: RETURN PsiMethod:foo<-PsiLiteralExpression:0
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(Object o) {
        if (o == null) return new NewMethodResult((1 /* exit key */), 0);
        return new NewMethodResult((-1 /* exit key */), (0 /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private int returnResult;

        public NewMethodResult(int exitKey, int returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}