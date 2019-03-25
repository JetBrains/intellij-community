class C {
    int foo(String[] vars, int i) {
        return vars[i].length();
    }//ins and outs
//in: PsiParameter:i
//in: PsiParameter:vars
//exit: EXPRESSION PsiMethodCallExpression:vars[i].length()

    public NewMethodResult newMethod(String[] vars, int i) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}