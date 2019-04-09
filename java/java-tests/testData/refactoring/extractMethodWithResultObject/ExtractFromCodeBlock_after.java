class Test {
    void method() {
        System.out.println("1");
        NewMethodResult x = newMethod();
        System.out.println("4");
    }

    NewMethodResult newMethod() {
        {
             System.out.println("2");
             System.out.println("3");
        }
        return new NewMethodResult();
    }

    static class NewMethodResult {
        public NewMethodResult() {
        }
    }
}