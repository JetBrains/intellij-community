class C {
    void foo() {
        newMethod();
    }

    private void newMethod() {
        /*a*/ // b
        System.out.println(1);
        /*c*/ // d
    }

    void bar() {
        /*x*/
        newMethod();
        /*z*/
    }
}