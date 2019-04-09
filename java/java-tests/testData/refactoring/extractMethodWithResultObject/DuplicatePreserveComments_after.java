class C {
    void foo() {
        NewMethodResult x = newMethod();
    }

    NewMethodResult newMethod() {/*a*/ // b
        System.out.println(1);
        /*c*/ // d
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }

    void bar() {
        /*x*/
        System.out.println(1);
        /*z*/
    }
}