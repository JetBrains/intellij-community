class DoIfWhile {
    String foo(int a, boolean b) {
        int x = 0;
        do {
            NewMethodResult x1 = newMethod(b, x);
            if (x1.exitKey == 1) return x1.returnResult;
        }
        while (++x < a);

        return null;
    }

    NewMethodResult newMethod(boolean b, int x) {
        {
        /*comment*/ if (b) {
            String s = bar(x);
            if (s != null) return new NewMethodResult((1 /* exit key */), s);
        }
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

    String bar(int x) { return "";}
}