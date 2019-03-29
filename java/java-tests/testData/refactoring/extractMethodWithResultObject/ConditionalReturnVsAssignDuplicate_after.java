class Conditional {
    int bar(String s) {
        NewMethodResult x = newMethod(s);
        if (s != null) {
            int n = s.length();
            return n;
        }
        return 0;
    }//ins and outs
//in: PsiParameter:s
//exit: RETURN PsiMethod:bar<-PsiReferenceExpression:n
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(String s) {
        if (s != null) {
            int n = s.length();
            return new NewMethodResult((1 /* exit key */), n);
        }
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

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length();
            x = n;
        }
        return 0;
    }
}
