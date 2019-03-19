class C {
    String[] vars;
    int foo(C c, int i) {
        return c.vars[i].length();
    }//ins and outs
//in: PsiParameter:c
//in: PsiParameter:i
//out: EXPRESSION PsiMethodCallExpression:c.vars[i].length()
}