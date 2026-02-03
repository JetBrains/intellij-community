class TestSuper {
    public static class A {
        private String s = "A";

        public void foo() {
            System.out.println("A::foo");
        }
    }

    public static class B extends A {

        public void foo() {
            super.foo();
            System.out.println("B::foo " + super.s);
            System.out.println("B::foo " + ((A) this).s);
        }
    }

    public static void main(String[] args) {
        new B().foo();
    }
}