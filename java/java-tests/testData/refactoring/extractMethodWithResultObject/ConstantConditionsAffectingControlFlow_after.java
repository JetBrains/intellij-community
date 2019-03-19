public /*abstract*/ class WillWorkTest {
    int opera() {
        int i = 0;

        int k;
        if (true) k = i;
        return k;
    }//ins and outs
//in: PsiLocalVariable:i
//out: PsiLocalVariable:k
//exit: RETURN PsiMethod:opera<-PsiReferenceExpression:k
}
