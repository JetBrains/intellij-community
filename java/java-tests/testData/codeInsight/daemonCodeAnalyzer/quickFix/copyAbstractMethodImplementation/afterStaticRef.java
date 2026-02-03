// "Use existing implementation of 'm'" "true"
interface I {
    void m();
}

class A implements I {
    public static final String TEXT = "code";

    public void m() {
        System.out.println(TEXT);
    }
}

class B implements I {
    public void m() {
        <selection>System.out.println(A.TEXT);</selection>
    }
}
