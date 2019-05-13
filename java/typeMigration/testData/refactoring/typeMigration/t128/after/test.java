class Test {
    static class E extends Exception { }
    static class E1 extends E { }
    static class E2 extends E { }

    void m(boolean f) {
        try {
            if (f)
                throw new E1();
            else
                throw new E2();
        } catch (E e) {
            e.printStackTrace();
        }
    }
}