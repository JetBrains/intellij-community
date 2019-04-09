class OutputVariableReused {

    static class X {
        X(String s) {}
    }

    String convert(String s, String s1, String s2) {
        return s + s1 + s2;
    }

    public X test(String s, String left, String right) {
        NewMethodResult x = newMethod(s, left, right);
        if (x.exitKey == 1) return x.returnResult;
        String res;
        res = convert(s, right, left);
        if (res != null) {
            return new X(res);
        }
        return null;
    }

    NewMethodResult newMethod(String s, String left, String right) {
        String res = convert(s, left, right);
        if (res != null) {
            return new NewMethodResult((1 /* exit key */), new X(res), (null /* missing value */));
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private X returnResult;
        private String res;

        public NewMethodResult(int exitKey, X returnResult, String res) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
            this.res = res;
        }
    }
}
