class A {
    private Object b = new Inner();

    private class <caret>Inner {
        public Inner(int i) {
        }

        public String toString() {
            return "A";
        }
    }
}