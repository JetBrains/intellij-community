class X {
  void foo(java.util.List l) {
    for (Object o : l) {
        NewMethodResult x1 = newMethod(o);
        if (x1.exitKey == 1) continue;
        String x = x1.x;
        System.out.println(x);
    }
  }

    NewMethodResult newMethod(Object o) {
        if (o == null) return new NewMethodResult((1 /* exit key */), (null /* missing value */));
        String x = bar(o);
        return new NewMethodResult((-1 /* exit key */), x);
    }

    static class NewMethodResult {
        private int exitKey;
        private String x;

        public NewMethodResult(int exitKey, String x) {
            this.exitKey = exitKey;
            this.x = x;
        }
    }

    private String bar(Object o) {
    return "";
  }
}
