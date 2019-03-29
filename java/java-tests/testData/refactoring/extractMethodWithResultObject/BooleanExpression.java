class Test {
    void method(int i) {
        boolean isDirty = <selection>i == 0</selection> || otherTests();
    }
    boolean otherTests() { return true; }
}