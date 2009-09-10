class Test {
    private class <caret>A {
        private B b = new B();
        private class B {
        }
    }
}