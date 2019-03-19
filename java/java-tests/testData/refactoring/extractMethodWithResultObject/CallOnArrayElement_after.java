class C {
    int foo(String[] vars, int i) {
        return vars[i].length();
    }//ins and outs
//in: PsiParameter:i
//in: PsiParameter:vars
//out: EXPRESSION PsiMethodCallExpression:vars[i].length()
}