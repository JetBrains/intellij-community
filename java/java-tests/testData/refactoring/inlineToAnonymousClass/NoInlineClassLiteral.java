class A {
    private Object b = new Inner();

    public void test() {
        System.out.println(Inner.class.getName());
    }

    private class <caret>Inner {
        public String toString() {
            return "A";
        }
    }
}