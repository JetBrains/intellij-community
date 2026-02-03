// "Use existing implementation of 'm'" "true"
interface I {
    void <caret>m();
}

class A implements I {
    public void m() {
        System.out.println("code");
    }
}

class B implements I {
}
