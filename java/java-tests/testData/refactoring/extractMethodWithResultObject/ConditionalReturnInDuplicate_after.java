class Conditional {
    int[] bar(String[] s) {

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
//exit count: 2

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
