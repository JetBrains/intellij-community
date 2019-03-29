class C {
    String method(Object o) {
        System.out.println(o);
        Integer i = new Integer(o.hashCode());
        return i.toString();
    }//ins and outs
//in: PsiParameter:o
//exit: RETURN PsiMethod:method<-PsiMethodCallExpression:i.toString()

    NewMethodResult newMethod(Object o) {
        Integer i = new Integer(o.hashCode());
        return new NewMethodResult(i.toString());
    }

    class NewMethodResult {
        private String returnResult;

        public NewMethodResult(String returnResult) {
            this.returnResult = returnResult;
        }
    }

    {
        Integer j = new Integer(Boolean.TRUE.hashCode());
        String k = j.toString();
    }
}