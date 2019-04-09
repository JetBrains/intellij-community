class Foo {
    boolean bar(String[] a) {
        for (int i = 0; i < a.length; i++) {
            NewMethodResult x = newMethod(a, i);
            if (x.exitKey == 1) return x.returnResult;
        }
        return false;
    }

    NewMethodResult newMethod(String[] a, int i) {
        if (a[i].length() > 3 && i % 3 == 0)
            return new NewMethodResult((1 /* exit key */), true);
        return new NewMethodResult((-1 /* exit key */), (false /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private boolean returnResult;

        public NewMethodResult(int exitKey, boolean returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}
