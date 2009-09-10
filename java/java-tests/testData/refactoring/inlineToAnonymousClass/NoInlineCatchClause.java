class A {
    public void test() {
        try {
            test2();
        }
        catch(Inner ex) {
        }
    }

    private void test2() {
    }

    private class <caret>Inner extends RuntimeException {
        public String toString() {
            return "A";
        }
    }
}