class ElseIf {
    String foo(boolean a, boolean b) {
        if (a) {
            NewMethodResult x = newMethod(b);
            if (x.exitKey == 1) return x.returnResult;
        }

        return null;
    }

    NewMethodResult newMethod(boolean b) {
        if (b) {
            String s = bar();
            if (s != null) return new NewMethodResult((1 /* exit key */), s);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private String returnResult;

        public NewMethodResult(int exitKey, String returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    String bar() { return "";}
}
