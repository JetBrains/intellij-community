class A {
    private Object b = new Inner();

    private class <caret>Inner {
        private String s;

        public Inner() {
            s=null;
        }

        public String toString() {
            if (s == null) {
                s = "q";
            }
            return "A";
        }
    }
}