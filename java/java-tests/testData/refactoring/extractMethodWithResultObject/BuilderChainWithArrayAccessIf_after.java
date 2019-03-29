class Foo {
    boolean bar(String[] a) {
        for (int i = 0; i < a.length; i++) {
            if (a[i].length() > 3 && i % 3 == 0)
                return true;
        }
        return false;
    }//ins and outs
//in: PsiLocalVariable:i
//in: PsiParameter:a
//exit: RETURN PsiMethod:bar<-PsiLiteralExpression:true
//exit: SEQUENTIAL PsiForStatement

    NewMethodResult newMethod(String[] a, int i) {
        if (a[i].length() > 3 && i % 3 == 0)
            return new NewMethodResult((1 /* exit key */), true);
        return new NewMethodResult((-1 /* exit key */), (false /* missing value */));
    }

    class NewMethodResult {
        private int exitKey;
        private boolean returnResult;

        public NewMethodResult(int exitKey, boolean returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}
