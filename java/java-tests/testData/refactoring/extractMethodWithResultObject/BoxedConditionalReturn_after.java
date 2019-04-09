class Test {
    public static Integer foo(Integer[] a) {

        NewMethodResult x = newMethod(a);
        if (x.exitKey == 1) return x.returnResult;
        return null;
    }

    static NewMethodResult newMethod(Integer[] a) {
        if (a.length != 0) {
            int n = a[0] != null ? a[0] : 0;
            return new NewMethodResult((1 /* exit key */), n);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private Integer returnResult;

        public NewMethodResult(int exitKey, Integer returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }

    public static Integer bar(Integer[] a) {
        if (a.length != 0) {
            int n = a[0] != null ? a[0] : 0;
            return n;
        }
        return null;
    }
}