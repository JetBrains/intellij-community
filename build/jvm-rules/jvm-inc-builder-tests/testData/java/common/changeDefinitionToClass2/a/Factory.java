class Factory {
  public static Server createServer() {
    return new Server() {
      public void foo() {
        System.out.println("foo() called");
      }
    };
  }
}