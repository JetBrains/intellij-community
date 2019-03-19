class Test {
    public static Integer foo(Integer[] a) {

        if (a.length != 0) {
            int n = a[0] != null ? a[0] : 0;
            return n;
        }
        return null;
    }//ins and outs
//in: PsiParameter:a
//out: INSIDE PsiReferenceExpression:n
//out: OUTSIDE null

    public static Integer bar(Integer[] a) {
        if (a.length != 0) {
            int n = a[0] != null ? a[0] : 0;
            return n;
        }
        return null;
    }
}