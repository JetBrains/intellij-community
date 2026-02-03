class Test {
  private String foo;

  public void test() {
    boolean rangeMarkersDisposed = false;
    try {
      if (foo == "dd") {
        throw new RuntimeException();
      }
      rangeMarkersDisposed = true;
    }
    finally {
      try {
      }
      finally {
      }

      if (!rangeMarkersDisposed) {
        foo = "dd";
      }
    }
  }
}