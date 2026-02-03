class Test {
    Object object;
    void foo() {
        final Object object = this.object;
        Object o = object; // object is selected
        Object p = object;
    }
}