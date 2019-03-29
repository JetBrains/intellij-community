class Conditional {
    int[] bar(String[] s) {

        NewMethodResult x = newMethod(s);
        if (s != null) {
            int[] n = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                n[i] = s[i].length();
            }
            return n;
        }
        return new int[0];
    }//ins and outs
//in: PsiParameter:s
//exit: RETURN PsiMethod:bar<-PsiReferenceExpression:n
//exit: SEQUENTIAL PsiIfStatement

    NewMethodResult newMethod(String[] s) {
        if (s != null) {
            int[] n = new int[s.length];
            for (int i = 0; i < s.length; i++) {
                n[i] = s[i].length();
            }
            return new NewMethodResult((1 /* exit key */), n);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private int[] returnResult;

        public NewMethodResult(int exitKey, int[] returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    int[] baz(String[] z) {
        if (z != null) {
            int[] n = new int[z.length];
            for (int i = 0; i < z.length; i++) {
                n[i] = z[i].length();
            }
            return n;
        }
        return new int[0];
    }
}
