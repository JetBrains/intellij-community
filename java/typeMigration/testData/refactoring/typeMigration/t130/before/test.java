class Test {
    static class E extends Exception { }
    static interface I { void i(); }
    static class E1 extends E implements I { public void i() { } }
    static class E2 extends E implements I { public void i() { } }

    void m(boolean f) {
        try {
            if (f)
                throw new E1();
            else
                throw new E2();
        } catch (E1 | E2 e) {
            e.printStackTrace();
            e.i();
        }
    }
}