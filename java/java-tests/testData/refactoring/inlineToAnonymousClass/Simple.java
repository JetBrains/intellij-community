class A {
    private Object b = new Inner();

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}