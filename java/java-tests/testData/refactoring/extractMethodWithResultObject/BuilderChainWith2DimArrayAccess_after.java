class Foo {
    boolean bar(String[][] a) {
        for (int i = 0; i < a.length; i++)
            for (int j = 0; i < a[i].length; j++) {
                NewMethodResult x = newMethod(a, i, j);
                if (x.exitKey == 1) return x.returnResult;

            }
        return false;
    }

    NewMethodResult newMethod(String[][] a, int i, int j) {
        if (a[i][j].length() > 3 && i % 3 == 0)
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
