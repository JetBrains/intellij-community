class C {
    String method(Object o) {
        System.out.println(o);
        Integer i = new Integer(o.hashCode());
        return i.toString();
    }//ins and outs
//in: PsiParameter:o
//exit: RETURN PsiMethod:method<-PsiMethodCallExpression:i.toString()

    public NewMethodResult newMethod(Object o) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }

    {
        Integer j = new Integer(Boolean.TRUE.hashCode());
        String k = j.toString();
    }
}