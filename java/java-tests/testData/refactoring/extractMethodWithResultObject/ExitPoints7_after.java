class Foo {
   public Object createBean(Object parent) {
    try {
        NewMethodResult x = newMethod(parent);
        if (x.exitKey == 1) return x.returnResult;
        Object tag = x.tag;

        tag = foo(tag);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }

    return null;
  }

    NewMethodResult newMethod(Object parent) {
        if (parent != null) {
          try {
          }
          catch (Exception e) {
              return new NewMethodResult((1 /* exit key */), null, (null /* missing value */));
          }
        }


        Object tag = null;
        return new NewMethodResult((-1 /* exit key */), (null /* missing value */), tag);
    }

    static class NewMethodResult {
        private int exitKey;
        private Object returnResult;
        private Object tag;

        public NewMethodResult(int exitKey, Object returnResult, Object tag) {
            this.exitKey = exitKey;
            this.returnResult = returnResult;
            this.tag = tag;
        }
    }

    private Object foo(final Object tag) {
    return null;
  }
}