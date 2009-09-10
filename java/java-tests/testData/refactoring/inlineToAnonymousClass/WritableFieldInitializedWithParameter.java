class A {
    public void doTest() {
        int i = 2;
        Object b = new Inner(i*2);
    }

    private class <caret>Inner {
        private int i;

        public Inner(int arg) {
            i=arg;
        }

        public String toString() {
            i++;
            return "A";
        }
    }
}