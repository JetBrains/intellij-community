class WillWorkTest {
    int opera() {
        int i = 0;

        NewMethodResult x = newMethod(i);
        return x.returnResult;
    }//ins and outs
//in: PsiLocalVariable:i
//out: PsiLocalVariable:k
//exit: RETURN PsiMethod:opera<-PsiReferenceExpression:k

    NewMethodResult newMethod(int i) {
        int k;
        if (true) k = i;
        return new NewMethodResult(k, k);
    }

    static class NewMethodResult {
        private int returnResult;
        private int k;

        public NewMethodResult(int returnResult, int k) {
            this.returnResult = returnResult;
            this.k = k;
        }
    }
}
