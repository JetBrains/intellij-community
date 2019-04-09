class Conditional {
    int bar(String s) {
        NewMethodResult x = newMethod(s);
        if (x.exitKey == 1) return x.returnResult;
        return 0;
    }

    NewMethodResult newMethod(String s) {
        if (s != null) {
            int n = s.length();
            return new NewMethodResult((1 /* exit key */), n);
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

    int baz(String z) {
        int x = -1;
        if (z != null) {
            int n = z.length();
            x = n;
        }
        return 0;
    }
}
