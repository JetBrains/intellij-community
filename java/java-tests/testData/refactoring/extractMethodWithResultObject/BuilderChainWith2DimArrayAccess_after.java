class Foo {
    boolean bar(String[][] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; i < a[i].length; j++) {
                if (a[i][j].length() > 3 && i % 3 == 0)
                    return true;

        }
        return false;
    }//ins and outs
//in: PsiLocalVariable:i
//in: PsiLocalVariable:j
//in: PsiParameter:a
//out: INSIDE PsiLiteralExpression:true
//out: OUTSIDE null
}
