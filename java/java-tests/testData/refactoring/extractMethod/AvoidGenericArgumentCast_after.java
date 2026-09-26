class C {
    <K> void f(K k) {
        newMethod(k);
    }

    private <K> void newMethod(K k) {
        System.out.println(k);
    }

    void g() {
        Object o = "";
        newMethod(o);
    }
}