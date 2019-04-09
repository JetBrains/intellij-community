class C {
  String test(int n) {
    String s = "";

      NewMethodResult x = newMethod(n, s);
      if (x.exitKey == 1) return x.returnResult;

      return null;
  }

    NewMethodResult newMethod(int n, String s) {
        if (n == 1) {
            return new NewMethodResult((1 /* exit key */), "A" + s);
        }
        if (n == 2) {
            return new NewMethodResult((1 /* exit key */), "B" + s);
        }
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */));
    }

    static class NewMethodResult {
        private int exitKey;
        private String returnResult;

        public NewMethodResult(int exitKey, String returnResult) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
        }
    }
}