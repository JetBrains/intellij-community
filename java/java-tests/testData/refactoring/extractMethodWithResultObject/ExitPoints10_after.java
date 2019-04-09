class C {
    private int[] list;

    private Integer find(int id) {

        NewMethodResult x = newMethod(id);
        if (x.exitKey == 1) return x.returnResult;
        int n;

        throw new RuntimeException();
    }

    NewMethodResult newMethod(int id) {
        int n = 0;
        for (int n1 : list) {
            n = n1;
            if (n == id) {
                return new NewMethodResult((1 /* exit key */), n <= 0 ? null : n, (0 /* missing value */));
            }
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */), (0 /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private Integer returnResult;
        private int n;

        public NewMethodResult(int exitKey, Integer returnResult, int n) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
            this.n = n;
        }
    }
}