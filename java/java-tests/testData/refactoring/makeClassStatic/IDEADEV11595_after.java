class Test {
    private static class A {
        private B b = new B();

        public A() {
        }

        private class B {
        }
    }
}