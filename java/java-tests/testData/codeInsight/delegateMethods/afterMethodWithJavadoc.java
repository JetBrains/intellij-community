class List {
    /**
     * javadoc must be present, see SCR 39108
    */
    void foo() {}
}

class Test {
    List l;

    public void foo() {
        l.foo();
    }
}