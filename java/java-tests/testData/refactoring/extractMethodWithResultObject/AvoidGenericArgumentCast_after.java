class C {
    <K> void f(K k) {
        System.out.println(k);
    }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:f

    void g() {
        Object o = "";
        System.out.println(o);
    }
}