class C {
  String[] array;

  boolean test(String a, String b) {
    for (String s : array) {
      if (a.equals(s)) {

          NewMethodResult x = newMethod(b, s);
          if (x.exitKey == 1) return x.returnResult;

      }
    }
    return false;
  }

    NewMethodResult newMethod(String b, String s) {
        if (b == null) {
            return new NewMethodResult((1 /* exit key */), true);
        }
        if (b.equals(s)) {
            return new NewMethodResult((1 /* exit key */), true);
        }
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