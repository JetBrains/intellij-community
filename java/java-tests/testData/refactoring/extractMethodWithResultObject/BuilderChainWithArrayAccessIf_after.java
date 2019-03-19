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
//out: PsiLiteralExpression:true
    //zz
}
