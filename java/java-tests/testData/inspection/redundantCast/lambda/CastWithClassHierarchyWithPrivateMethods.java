class C {
    static class A {
        private void m1() {
        }
    }

    static class B extends A {
        private void m1() {
        }
    }

    void test() {
        A a = new B();
        ((B) a).m1();
    }
}