class A {
    private Object b = new Inner("q");

    private class <caret>Inner extends Someshit {
        public String toString() {
            return "A";
        }
    }
}