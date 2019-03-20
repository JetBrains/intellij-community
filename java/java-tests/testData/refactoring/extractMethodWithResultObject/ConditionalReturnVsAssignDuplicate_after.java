class Conditional {
    int bar(String s) {
        if (s != null) {
            int n = s.length;
            return n;
        }
        return 0;
    }//ins and outs
//in: PsiParameter:s
//exit: RETURN PsiMethod:bar<-PsiReferenceExpression:n
//exit: SEQUENTIAL PsiIfStatement

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length;
            x = n;
        }
        return 0;
    }
}
