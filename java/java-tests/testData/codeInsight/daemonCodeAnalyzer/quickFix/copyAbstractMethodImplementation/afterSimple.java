// "Use existing implementation of 'm'" "true"
interface I {
    void m();
}

class A implements I {
    public void m() {
        System.out.println("code");
    }
}

class B implements I {
    public void m() {
        <selection>System.out.println("code");</selection>
    }
}
