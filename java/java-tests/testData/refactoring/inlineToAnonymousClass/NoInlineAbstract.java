class A {
    private Object b = new Inner();

    public abstract class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}