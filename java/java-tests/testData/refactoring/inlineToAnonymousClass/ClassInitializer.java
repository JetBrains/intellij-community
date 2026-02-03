class A {
    private Object b = new Inner();

    private class <caret>Inner {
        {
            // class initializer
        }

        public String toString() {
            return "A";
        }
    }
}