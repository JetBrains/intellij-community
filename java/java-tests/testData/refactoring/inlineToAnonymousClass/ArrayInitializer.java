class A {
    public void test() {
        Inner[] b = new Inner[] { new Inner() };
    }

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}