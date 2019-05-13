package p.b;

class A {
    protected A() {
    }

    private class B extends A {
        private B() {
            System.out.println("Constructor");
        }
    }
}