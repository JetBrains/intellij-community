class C {
    <K> void f(K k) {
        NewMethodResult x = newMethod(k);
    }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:f

    <K> NewMethodResult newMethod(K k) {
        System.out.println(k);
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    void g() {
        Object o = "";
        System.out.println(o);
    }
}