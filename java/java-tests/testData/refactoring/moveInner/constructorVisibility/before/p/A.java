package p;

class A {
    public void test() {
        B b = new B();
    }

    private class B {
        private B() {
            System.out.println("Constructor");
        }
    }
}