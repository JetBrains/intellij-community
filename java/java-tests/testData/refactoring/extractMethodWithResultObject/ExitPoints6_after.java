// multiple output values: one for modelling control flow + output value
class K {
    int f(Object o) {
        NewMethodResult x = newMethod(o);
        if (x.exitKey == 1) return x.returnResult;
        o = x.o;
        Object oo = o;

        return 1;
    }

    NewMethodResult newMethod(Object o) {
        if (o == null) return new NewMethodResult((1 /* exit key */), 0, (null /* missing value */));
        o = new Object();
        return new NewMethodResult((-1 /* exit key */), (0 /* missing value */), o);
    }

    static class NewMethodResult {
        private int exitKey;
        private int returnResult;
        private Object o;

        public NewMethodResult(int exitKey, int returnResult, Object o) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
            this.o = o;
        }
    }
}
