class Tester {
    String x() {
        String o = "";
        NewMethodResult x = newMethod(o);
        return x.returnResult;
    }

    NewMethodResult newMethod(String o) {
        String s;
        try {
            s = o;
        }
        finally {
        }
        return new NewMethodResult(s, (null /* missing value */));
    }

    static class NewMethodResult {
        private String returnResult;
        private String s;

        public NewMethodResult(String returnResult, String s) {
            this.returnResult = returnResult;
            this.s = s;
        }
    }
}