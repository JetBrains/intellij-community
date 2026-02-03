class A {
    private Object b = new Inner("q");

    private class <caret>Inner implements Someshit {
        public String toString() {
            return "A";
        }
    }
}