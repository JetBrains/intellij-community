class WillWorkTest {
    int opera(boolean b) {
        int i = 0;
        int k;
        if (b) k = 2;
        else k = 3;

        NewMethodResult x = newMethod(i, k);
        return x.returnResult;
    }

    NewMethodResult newMethod(int i, int k) {
        if (true) k = i;
        return new NewMethodResult(k, (0 /* missing value */));
    }

    static class NewMethodResult {
        private int returnResult;
        private int k;

        public NewMethodResult(int returnResult, int k) {
            this.returnResult = returnResult;
            this.k = k;
        }
    }
}
