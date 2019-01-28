class ConvertAnonymousToInner {

  boolean myFast = true;

  void convert(boolean fast) {
    new MyObject(fast);
  }

    private class MyObject {
        private final boolean myFast;

        public MyObject(boolean fast) {
            myFast = fast;
        }

        public String toString() {
          if (ConvertAnonymousToInner.this.myFast != myFast) {
            ConvertAnonymousToInner.this.myFast = myFast;
            System.out.println(myFast);
          }
          return "";
        }
    }
}