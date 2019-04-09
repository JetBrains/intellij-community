class Test {
    int method() {
        try {
            System.out.println("Text");
            return 0;
        } finally {
            NewMethodResult x = newMethod();
            return x.returnResult;
        }
    }

    NewMethodResult newMethod() {
        System.out.println("!!!");
        return new NewMethodResult(1);
    }

    static class NewMethodResult {
        private int returnResult;

        public NewMethodResult(int returnResult) {
            this.returnResult = returnResult;
        }
    }
}