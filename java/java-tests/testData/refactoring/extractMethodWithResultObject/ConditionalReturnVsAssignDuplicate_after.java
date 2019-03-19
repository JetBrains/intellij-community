class Conditional {
    int bar(String s) {
        if (s != null) {
            int n = s.length;
            return n;
        }
        return 0;
    }//ins and outs
//in: PsiParameter:s
//out: INSIDE PsiReferenceExpression:n
//out: OUTSIDE null

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length;
            x = n;
        }
        return 0;
    }
}
