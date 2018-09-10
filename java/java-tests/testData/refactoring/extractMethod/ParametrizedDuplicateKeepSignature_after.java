class KeepSignature {
    void hello() {
        newMethod();
        System.out.println("Bar");
        newMethod();
    }

    private void newMethod() {
        System.out.println("Foo");
    }
}