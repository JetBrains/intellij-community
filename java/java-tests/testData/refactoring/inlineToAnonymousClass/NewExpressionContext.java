class A {
    private Object b = new Inner();
    private Object c = new Object() {
        public void doStuff(Inner i) {
        }
    };

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}