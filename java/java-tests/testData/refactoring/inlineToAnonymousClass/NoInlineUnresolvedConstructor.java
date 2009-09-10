class A {
    private Object b = new Inner("q");

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}