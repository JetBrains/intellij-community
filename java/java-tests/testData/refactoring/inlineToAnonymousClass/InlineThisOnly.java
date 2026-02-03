class A {
    private Inner b = new <caret>Inner();
    private Inner b2 = new Inner();

    private class Inner {
        public String toString() {
            return "A";
        }
    }
}