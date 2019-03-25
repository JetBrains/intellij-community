class C {
    <K> void f(K k) {
        System.out.println(k);
    }//ins and outs
//in: PsiParameter:k
//exit: SEQUENTIAL PsiMethod:f

    public NewMethodResult newMethod(K k) {
        return new NewMethodResult();
    }

    public class NewMethodResult {
    }

    void g() {
        Object o = "";
        System.out.println(o);
    }
}