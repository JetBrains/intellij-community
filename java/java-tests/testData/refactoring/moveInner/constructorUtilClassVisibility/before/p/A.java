package p;

class A {
    public void test() {
        B.foo();
    }

    private class B {
        private B() {
            System.out.println("Constructor");
        }

        static void foo(){}
    }
}