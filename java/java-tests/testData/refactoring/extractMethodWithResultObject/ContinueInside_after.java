class Test {
  String foo(String[] args) {

      NewMethodResult x = newMethod(args);
      if (x.exitKey == 1) return x.returnResult;

      return null;
  }

    NewMethodResult newMethod(String[] args) {
        for(String arg : args) {
          if (arg == null) continue;
          System.out.println(arg);
        }
        if (args.length == 0) return new NewMethodResult((1 /* exit key */), null);
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
