class Test {
    int method(boolean cond1, boolean cond2) {
        NewMethodResult x = newMethod(cond1, cond2);
        if (x.exitKey == 1) return x.returnResult;
        return 12;
    }

    NewMethodResult newMethod(boolean cond1, boolean cond2) {
        try {
            if(cond1) return new NewMethodResult((1 /* exit key */), 0);
            else if(cond2) return new NewMethodResult((1 /* exit key */), 1);
            System.out.println("Text");
        } finally {
            doSomething();
        }
        return new NewMethodResult((-1 /* exit key */), (0 /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private int returnResult;

        public NewMethodResult(int exitKey, int returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    void doSomething() {}
}