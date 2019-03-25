class Test {

    void foo(Object x) {
        if (x instanceof String) x = ((String)x).substring(1);
        if (x instanceof String) x = ((String)x).substring(1);
    }//ins and outs
//in: PsiParameter:x
//exit: EXPRESSION PsiMethodCallExpression:((String)x).substring(1)

    public NewMethodResult newMethod(Object x) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }
}