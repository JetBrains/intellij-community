class Test {
    void method() {
        System.out.println("1");
        newMethod();
        System.out.println("4");
    }

    private void newMethod() {
        System.out.println("2");
        System.out.println("3");
    }
}