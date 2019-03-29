class C {
    <K> void f(K k) {
        System.out.println(k);
    }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:f

    NewMethodResult newMethod(K k) {
        System.out.println(k);
        return new NewMethodResult();
    }

    class NewMethodResult {
        public NewMethodResult() {
        }
    }

    void g() {
        Object o = "";
        System.out.println(o);
    }
}