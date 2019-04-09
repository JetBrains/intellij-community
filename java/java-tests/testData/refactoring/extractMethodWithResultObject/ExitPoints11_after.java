class C {
    private int[] list;

    private int find(int id) {
        NewMethodResult x = newMethod(id);
        if (x.exitKey == 1) return x.returnResult;
        throw new RuntimeException();
    }

    NewMethodResult newMethod(int id) {
        for (int n : list) {
            if (n == id) {
                return new NewMethodResult((1 /* exit key */), n <= 0 ? 0 : n);
            }
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
}